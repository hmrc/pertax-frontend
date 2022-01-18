/*
 * Copyright 2022 HM Revenue & Customs
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
import controllers.bindable.ResidentialAddrType
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.SubmittedInternationalAddressChoiceId
import models.dto.InternationalAddressChoiceDto
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.renderer.TemplateRenderer
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.InternationalAddressChoiceView

import scala.concurrent.{ExecutionContext, Future}

class DoYouLiveInTheUKController @Inject() (
  cachingHelper: AddressJourneyCachingHelper,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  internationalAddressChoiceView: InternationalAddressChoiceView,
  displayAddressInterstitialView: DisplayAddressInterstitialView
)(implicit configDecorator: ConfigDecorator, templateRenderer: TemplateRenderer, ec: ExecutionContext)
    extends AddressController(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) {

  def onPageLoad: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
          cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
            Future.successful(
              Ok(internationalAddressChoiceView(InternationalAddressChoiceDto.form(), ResidentialAddrType))
            )
          }
        }
      }
    }

  def onSubmit: Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        InternationalAddressChoiceDto
          .form()
          .bindFromRequest
          .fold(
            formWithErrors =>
              Future.successful(BadRequest(internationalAddressChoiceView(formWithErrors, ResidentialAddrType))),
            internationalAddressChoiceDto =>
              cachingHelper.addToCache(SubmittedInternationalAddressChoiceId, internationalAddressChoiceDto) map { _ =>
                if (internationalAddressChoiceDto.value) {
                  Redirect(routes.PostcodeLookupController.onPageLoad(ResidentialAddrType))
                } else {
                  if (configDecorator.updateInternationalAddressInPta) {
                    Redirect(routes.UpdateInternationalAddressController.onPageLoad(ResidentialAddrType))
                  } else {
                    Redirect(routes.AddressErrorController.cannotUseThisService(ResidentialAddrType))
                  }
                }
              }
          )

      }
    }
}
