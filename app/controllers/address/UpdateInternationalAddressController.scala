/*
 * Copyright 2021 HM Revenue & Customs
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
import controllers.controllershelpers.{AddressJourneyCachingHelper, CountryHelper}
import models.dto.{AddressDto, DateDto}
import models.{SubmittedAddressDtoId, SubmittedStartDateId}
import org.joda.time.LocalDate
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.AuditServiceTools.buildAddressChangeEvent
import util.LocalPartialRetriever
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.UpdateInternationalAddressView

import scala.concurrent.{ExecutionContext, Future}

class UpdateInternationalAddressController @Inject()(
  countryHelper: CountryHelper,
  cachingHelper: AddressJourneyCachingHelper,
  auditConnector: AuditConnector,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  updateInternationalAddressView: UpdateInternationalAddressView,
  displayAddressInterstitialView: DisplayAddressInterstitialView)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressController(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { journeyData =>
        addressJourneyEnforcer { _ => personDetails =>
          typ match {
            case PostalAddrType =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("postalAddressChangeLinkClicked", personDetails, isInternationalAddress = true))
              cachingHelper.enforceDisplayAddressPageVisited(journeyData.addressPageVisitedDto) {
                Future.successful(
                  Ok(
                    updateInternationalAddressView(
                      journeyData.submittedAddressDto.fold(AddressDto.internationalForm)(
                        AddressDto.internationalForm.fill),
                      typ,
                      countryHelper.countries
                    )
                  )
                )
              }

            case _ =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("mainAddressChangeLinkClicked", personDetails, isInternationalAddress = true))
              cachingHelper.enforceResidencyChoiceSubmitted(journeyData) { _ =>
                Future.successful(
                  Ok(
                    updateInternationalAddressView(AddressDto.internationalForm, typ, countryHelper.countries)
                  )
                )
              }
          }
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { _ =>
        addressJourneyEnforcer { _ => _ =>
          AddressDto.internationalForm.bindFromRequest.fold(
            formWithErrors => {
              Future.successful(
                BadRequest(updateInternationalAddressView(formWithErrors, typ, countryHelper.countries)))
            },
            addressDto => {
              cachingHelper.addToCache(SubmittedAddressDtoId(typ), addressDto) flatMap { _ =>
                typ match {
                  case PostalAddrType =>
                    cachingHelper.addToCache(SubmittedStartDateId(typ), DateDto(LocalDate.now()))
                    Future.successful(Redirect(routes.AddressSubmissionController.onPageLoad(typ)))
                  case _ =>
                    Future.successful(Redirect(routes.StartDateController.onPageLoad(typ)))
                }
              }
            }
          )
        }
      }
    }
}
