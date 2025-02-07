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
import controllers.bindable.{AddrType, PostalAddrType, ResidentialAddrType}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.InternalServerErrorView
import views.html.interstitial.DisplayAddressInterstitialView

import scala.concurrent.{ExecutionContext, Future}

class StartChangeOfAddressController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  startChangeOfAddressView: StartChangeOfAddressView,
  displayAddressInterstitialView: DisplayAddressInterstitialView,
  featureFlagService: FeatureFlagService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      displayAddressInterstitialView: DisplayAddressInterstitialView,
      featureFlagService,
      internalServerErrorView
    ) {

  def onPageLoad(addrType: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        val startNowUrl = addrType match {
          case PostalAddrType => routes.PostalDoYouLiveInTheUKController.onPageLoad.url
          case _              => routes.DoYouLiveInTheUKController.onPageLoad.url
        }

        Future.successful(
          addrType match {
            case ResidentialAddrType => Ok(startChangeOfAddressView(addrType, startNowUrl))
            case PostalAddrType      => Ok(startChangeOfAddressView(addrType, startNowUrl))
          }
        )
      }
    }

}
