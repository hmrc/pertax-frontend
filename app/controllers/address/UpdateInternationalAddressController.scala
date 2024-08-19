/*
 * Copyright 2023 HM Revenue & Customs
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
import controllers.auth.AuthJourney
import controllers.bindable.{AddrType, PostalAddrType}
import controllers.controllershelpers.{AddressJourneyCachingHelper, CountryHelper}
import models.dto.{AddressDto, DateDto}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import routePages.{SubmittedAddressPage, SubmittedStartDatePage}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditServiceTools.buildAddressChangeEvent
import views.html.InternalServerErrorView
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.UpdateInternationalAddressView

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class UpdateInternationalAddressController @Inject() (
  countryHelper: CountryHelper,
  cachingHelper: AddressJourneyCachingHelper,
  auditConnector: AuditConnector,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  updateInternationalAddressView: UpdateInternationalAddressView,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  featureFlagService: FeatureFlagService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      displayAddressInterstitialView,
      featureFlagService,
      internalServerErrorView
    ) {

  def onPageLoad(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { journeyData =>
        addressJourneyEnforcer { _ => personDetails =>
          typ match {
            case PostalAddrType =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("postalAddressChangeLinkClicked", personDetails, isInternationalAddress = true)
              )
              cachingHelper.enforceDisplayAddressPageVisited(
                Ok(
                  updateInternationalAddressView(
                    journeyData.submittedAddressDto.fold(AddressDto.internationalForm)(
                      AddressDto.internationalForm.fill
                    ),
                    typ,
                    countryHelper.countries
                  )
                )
              )

            case _ =>
              auditConnector.sendEvent(
                buildAddressChangeEvent("mainAddressChangeLinkClicked", personDetails, isInternationalAddress = true)
              )
              cachingHelper.enforceDisplayAddressPageVisited(
                Ok(
                  updateInternationalAddressView(AddressDto.internationalForm, typ, countryHelper.countries)
                )
              )
          }
        }
      }
    }

  def onSubmit(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      cachingHelper.gettingCachedJourneyData[Result](typ) { _ =>
        addressJourneyEnforcer { _ => _ =>
          AddressDto.internationalForm
            .bindFromRequest()
            .fold(
              formWithErrors =>
                Future.successful(
                  BadRequest(updateInternationalAddressView(formWithErrors, typ, countryHelper.countries))
                ),
              addressDto =>
                cachingHelper.addToCache(SubmittedAddressPage(typ), addressDto) flatMap { _ =>
                  typ match {
                    case PostalAddrType =>
                      cachingHelper.addToCache(SubmittedStartDatePage(typ), DateDto(LocalDate.now()))
                      Future.successful(Redirect(routes.AddressSubmissionController.onPageLoad(typ)))
                    case _              =>
                      Future.successful(Redirect(routes.StartDateController.onPageLoad(typ)))
                  }
                }
            )
        }
      }
    }
}
