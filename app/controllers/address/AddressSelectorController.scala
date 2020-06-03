/*
 * Copyright 2020 HM Revenue & Customs
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
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.{AddrType, PostalAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.dto.{AddressDto, AddressSelectorDto, DateDto}
import models.{SelectedAddressRecordId, SubmittedAddressDtoId, SubmittedStartDateId}
import org.joda.time.LocalDate
import play.api.Logger
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import util.PertaxSessionKeys.{filter, postcode}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.AddressSelectorView

import scala.concurrent.{ExecutionContext, Future}

class AddressSelectorController @Inject()(
  cachingHelper: AddressJourneyCachingHelper,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  addressSelectorView: AddressSelectorView,
  displayAddressInterstitialView: DisplayAddressInterstitialView)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressControllerHelper(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
        journeyData.recordSet match {
          case Some(set) =>
            Future.successful(
              Ok(
                addressSelectorView(
                  AddressSelectorDto.form,
                  set,
                  typ,
                  postcodeFromRequest,
                  filterFromRequest
                )
              )
            )
          case _ => Future.successful(Redirect(address.routes.PostcodeLookupController.onPageLoad(typ)))
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => personDetails =>
        cachingHelper.gettingCachedJourneyData(typ) { journeyData =>
          AddressSelectorDto.form.bindFromRequest.fold(
            formWithErrors => {

              journeyData.recordSet match {
                case Some(set) =>
                  Future.successful(
                    BadRequest(
                      addressSelectorView(
                        formWithErrors,
                        set,
                        typ,
                        postcodeFromRequest,
                        filterFromRequest
                      )
                    ))
                case _ =>
                  Logger.warn("Failed to retrieve Address Record Set from cache")
                  Future.successful(internalServerError)
              }
            },
            addressSelectorDto => {
              journeyData.recordSet
                .flatMap(_.addresses.find(_.id == addressSelectorDto.addressId.getOrElse(""))) match {
                case Some(addressRecord) =>
                  val addressDto = AddressDto.fromAddressRecord(addressRecord)

                  for {
                    _ <- cachingHelper.addToCache(SelectedAddressRecordId(typ), addressRecord)
                    _ <- cachingHelper.addToCache(SubmittedAddressDtoId(typ), addressDto)
                  } yield {
                    val postCodeHasChanged = !postcodeFromRequest
                      .replace(" ", "")
                      .equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                    (typ, postCodeHasChanged) match {
                      case (PostalAddrType, false) =>
                        Redirect(routes.UpdateAddressController.onPageLoad(typ))
                      case (_, true) => Redirect(routes.StartDateController.onPageLoad(typ))
                      case (_, false) =>
                        cachingHelper.addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now()))
                        Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                    }
                  }
                case _ =>
                  Logger.warn("Address selector was unable to find address using the id returned by a previous request")
                  Future.successful(internalServerError)
              }
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
