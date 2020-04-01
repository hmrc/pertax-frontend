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
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.{AddrType, PostalAddrType}
import models.dto.{AddressDto, DateDto}
import org.joda.time.LocalDate
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.LocalSessionCache
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever

import scala.concurrent.{ExecutionContext, Future}

class UpdateAddressController @Inject()(
  sessionCache: LocalSessionCache,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents
)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressBaseController(sessionCache, authJourney, withActiveTabAction, cc) {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.addressLookupServiceDown || journeyData.selectedAddressRecord.isEmpty
        addressJourneyEnforcer { _ => _ =>
          val addressForm = journeyData.getAddressToDisplay.fold(AddressDto.ukForm)(AddressDto.ukForm.fill)
          typ match {
            case PostalAddrType =>
              enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                Future.successful(
                  Ok(
                    views.html.personaldetails.updateAddress(
                      addressForm.discardingErrors,
                      typ,
                      journeyData.addressFinderDto,
                      journeyData.addressLookupServiceDown,
                      showEnterAddressHeader)))
              }
            case _ =>
              enforceResidencyChoiceSubmitted(journeyData) { journeyData =>
                Future.successful(
                  Ok(
                    views.html.personaldetails.updateAddress(
                      addressForm.discardingErrors,
                      typ,
                      journeyData.addressFinderDto,
                      journeyData.addressLookupServiceDown,
                      showEnterAddressHeader
                    )
                  )
                )
              }
          }
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.addressLookupServiceDown || journeyData.selectedAddressRecord.isEmpty
        addressJourneyEnforcer { _ => personDetails =>
          {
            AddressDto.ukForm.bindFromRequest.fold(
              formWithErrors => {
                Future.successful(
                  BadRequest(
                    views.html.personaldetails.updateAddress(
                      formWithErrors,
                      typ,
                      journeyData.addressFinderDto,
                      journeyData.addressLookupServiceDown,
                      showEnterAddressHeader
                    )
                  )
                )
              },
              addressDto => {
                addToCache(SubmittedAddressDtoId(typ), addressDto) flatMap {
                  _ =>
                    val postCodeHasChanged = !addressDto.postcode
                      .getOrElse("")
                      .replace(" ", "")
                      .equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                    (typ, postCodeHasChanged) match {
                      case (PostalAddrType, _) =>
                        cacheAndRedirect(typ)
                      case (_, false) =>
                        cacheAndRedirect(typ)
                      case (_, true) =>
                        Future.successful(Redirect(routes.StartDateController.onPageLoad(typ)))
                    }
                }
              }
            )
          }
        }
      }
    }

  private def cacheAndRedirect(typ: AddrType)(implicit hc: HeaderCarrier): Future[Result] =
    addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now())) map { _ =>
      Redirect(controllers.routes.AddressController.reviewChanges(typ))
    }
}
