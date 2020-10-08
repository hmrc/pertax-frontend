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
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.dto.{AddressDto, DateDto}
import models.{SubmittedAddressDtoId, SubmittedStartDateId}
import org.joda.time.LocalDate
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.EditAddressLockRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.UpdateAddressView

import scala.concurrent.{ExecutionContext, Future}

class UpdateAddressController @Inject()(
  cachingHelper: AddressJourneyCachingHelper,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  updateAddressView: UpdateAddressView,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  override val editAddressLockRepository: EditAddressLockRepository)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressController(
      authJourney,
      withActiveTabAction,
      cc,
      displayAddressInterstitialView,
      editAddressLockRepository) {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.addressLookupServiceDown || journeyData.selectedAddressRecord.isEmpty
        addressJourneyEnforcer(typ) { _ => _ =>
          typ match {
            case PostalAddrType =>
              cachingHelper.enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                val addressForm = journeyData.getAddressToDisplay.fold(AddressDto.ukForm)(AddressDto.ukForm.fill)
                Future.successful(
                  Ok(
                    updateAddressView(
                      addressForm.discardingErrors,
                      typ,
                      journeyData.addressFinderDto,
                      journeyData.addressLookupServiceDown,
                      showEnterAddressHeader)))
              }
            case _ =>
              cachingHelper.enforceResidencyChoiceSubmitted(journeyData) { journeyData =>
                val addressForm = journeyData.getAddressToDisplay.fold(AddressDto.ukForm)(AddressDto.ukForm.fill)
                Future.successful(
                  Ok(
                    updateAddressView(
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
      cachingHelper.gettingCachedJourneyData[Result](typ) { journeyData =>
        val showEnterAddressHeader = journeyData.addressLookupServiceDown || journeyData.selectedAddressRecord.isEmpty
        addressJourneyEnforcer(typ) { _ => personDetails =>
          {
            AddressDto.ukForm.bindFromRequest.fold(
              formWithErrors => {
                Future.successful(
                  BadRequest(
                    updateAddressView(
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
                cachingHelper.addToCache(SubmittedAddressDtoId(typ), addressDto) flatMap {
                  _ =>
                    val postCodeHasChanged = !addressDto.postcode
                      .getOrElse("")
                      .replace(" ", "")
                      .equalsIgnoreCase(personDetails.address.flatMap(_.postcode).getOrElse("").replace(" ", ""))
                    (typ, postCodeHasChanged) match {
                      case (PostalAddrType, _) =>
                        cacheStartDate(
                          typ,
                          Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                        )
                      case (_, false) =>
                        cacheStartDate(
                          typ,
                          Redirect(routes.AddressSubmissionController.onPageLoad(typ))
                        )
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

  private def cacheStartDate(typ: AddrType, redirect: Result)(implicit hc: HeaderCarrier): Future[Result] =
    cachingHelper.addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now())) map (_ => redirect)
}
