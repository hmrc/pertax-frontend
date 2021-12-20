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
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.TaxCreditsChoiceId
import models.dto.TaxCreditsChoiceDto
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.TaxCreditsService
import uk.gov.hmrc.renderer.TemplateRenderer
import views.html.InternalServerErrorView
import views.html.interstitial.DisplayAddressInterstitialView

import scala.concurrent.ExecutionContext

class TaxCreditsChoiceController @Inject() (
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  cachingHelper: AddressJourneyCachingHelper,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  taxCreditsService: TaxCreditsService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, templateRenderer: TemplateRenderer, ec: ExecutionContext)
    extends AddressController(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) {

  def onPageLoad: Action[AnyContent] = authenticate.async { implicit request =>
    addressJourneyEnforcer { nino => _ =>
      cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
        cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
          taxCreditsService.checkForTaxCredits(Some(nino)).map {
            case Some(true) =>
              cachingHelper.addToCache(TaxCreditsChoiceId, TaxCreditsChoiceDto(true))
              Redirect(configDecorator.tcsChangeAddressUrl)
            case Some(false) =>
              cachingHelper.addToCache(TaxCreditsChoiceId, TaxCreditsChoiceDto(false))
              Redirect(routes.DoYouLiveInTheUKController.onPageLoad())
            case None =>
              InternalServerError(internalServerErrorView())
          }
        }
      }
    }
  }
}
