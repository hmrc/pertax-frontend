/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.address

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.address
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.{AddrType, PostalAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.addresslookup.RecordSet
import models.dto.{AddressDto, AddressSelectorDto, DateDto}
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.JourneyCacheRepository
import routePages.{SelectedAddressRecordPage, SelectedRecordSetPage, SubmittedAddressPage, SubmittedStartDatePage}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import services.{AddressSelectorService, CitizenDetailsService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import util.PertaxSessionKeys.{filter, postcode}
import views.html.InternalServerErrorView
import views.html.personaldetails.AddressSelectorView

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class AddressSelectorController @Inject() (
  cachingHelper: AddressJourneyCachingHelper,
  journeyCacheRepository: JourneyCacheRepository,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  addressSelectorView: AddressSelectorView,
  addressSelectorService: AddressSelectorService,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      featureFlagService,
      errorRenderer,
      citizenDetailsService,
      internalServerErrorView
    )
    with Logging {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
      journeyCacheRepository.get(hc).map { userAnswers =>
        userAnswers.get(SelectedRecordSetPage(typ)) match {
          case Some(recordSet) =>
            val orderedSet = RecordSet(addressSelectorService.orderSet(recordSet.addresses))
            Ok(
              addressSelectorView(
                AddressSelectorDto.form,
                orderedSet,
                typ,
                postcodeFromRequest,
                filterFromRequest
              )
            )
          case None            =>
            Redirect(address.routes.PostcodeLookupController.onPageLoad(typ))
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
          AddressSelectorDto.form
            .bindFromRequest()
            .fold(
              formWithErrors =>
                journeyData.recordSet match {

                  case Some(set) =>
                    val orderedSet = RecordSet(addressSelectorService.orderSet(set.addresses))
                    Future.successful(
                      BadRequest(
                        addressSelectorView(
                          formWithErrors,
                          orderedSet,
                          typ,
                          postcodeFromRequest,
                          filterFromRequest
                        )
                      )
                    )
                  case _         =>
                    logger.warn("Failed to retrieve Address Record Set from cache")
                    errorRenderer.futureError(INTERNAL_SERVER_ERROR)
                },
              addressSelectorDto =>
                journeyData.recordSet
                  .flatMap(_.addresses.find(_.id == addressSelectorDto.addressId.getOrElse(""))) match {
                  case Some(addressRecord) =>
                    val addressDto = AddressDto.fromAddressRecord(addressRecord)

                    for {
                      _ <- cachingHelper.addToCache(SelectedAddressRecordPage(typ), addressRecord)
                      _ <- cachingHelper.addToCache(SubmittedAddressPage(typ), addressDto)
                    } yield {
                      val postCodeHasChanged = !postcodeFromRequest
                        .replace(" ", "")
                        .equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                      (typ, postCodeHasChanged) match {
                        case (PostalAddrType, false) =>
                          Redirect(routes.UpdateAddressController.onPageLoad(typ))
                        case (_, true)               => Redirect(routes.StartDateController.onPageLoad(typ))
                        case (_, false)              =>
                          cachingHelper.addToCache(SubmittedStartDatePage(typ), DateDto(LocalDate.now()))
                          Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                      }
                    }
                  case _                   =>
                    logger
                      .warn("Address selector was unable to find address using the id returned by a previous request")
                    errorRenderer.futureError(INTERNAL_SERVER_ERROR)
                }
            )
        }
      }
    }

  private def postcodeFromRequest(implicit request: UserRequest[AnyContent]): String =
    request.body.asFormUrlEncoded.flatMap(_.get(postcode).flatMap(_.headOption)).getOrElse("")

  private def filterFromRequest(implicit request: UserRequest[AnyContent]): Option[String] =
    request.body.asFormUrlEncoded.flatMap(_.get(filter).flatMap(_.headOption))

}
