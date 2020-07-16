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
import controllers.bindable.AddrType
import controllers.controllershelpers.AddressJourneyCachingHelper
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{AddressAlreadyUpdatedView, CannotUseServiceView}

import scala.concurrent.{ExecutionContext, Future}

class AddressErrorController @Inject()(
  authJourney: AuthJourney,
  cachingHelper: AddressJourneyCachingHelper,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  cannotUseServiceView: CannotUseServiceView,
  addressAlreadyUpdatedView: AddressAlreadyUpdatedView)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends AddressController(authJourney, withActiveTabAction, cc, displayAddressInterstitialView) {

  def cannotUseThisService(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        cachingHelper.gettingCachedAddressPageVisitedDto { addressPageVisitedDto =>
          cachingHelper.enforceDisplayAddressPageVisited(addressPageVisitedDto) {
            Future.successful(Ok(cannotUseServiceView(typ)))
          }
        }
      }
    }

  def showAddressAlreadyUpdated(typ: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        Future.successful(Ok(addressAlreadyUpdatedView()))
      }
    }
}
