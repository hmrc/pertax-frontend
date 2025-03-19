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
import controllers.bindable.AddrType
import error.ErrorRenderer
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.CitizenDetailsService
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.InternalServerErrorView
import views.html.personaldetails.StartChangeOfAddressView

import scala.concurrent.{ExecutionContext, Future}

class StartChangeOfAddressController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  startChangeOfAddressView: StartChangeOfAddressView,
  errorRenderer: ErrorRenderer,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  internalServerErrorView: InternalServerErrorView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends AddressController(
      authJourney,
      cc,
      featureFlagService: FeatureFlagService,
      errorRenderer: ErrorRenderer,
      citizenDetailsService: CitizenDetailsService,
      internalServerErrorView: InternalServerErrorView
    ) {

  def onPageLoad(addrType: AddrType): Action[AnyContent] =
    authenticate.async { implicit request =>
      addressJourneyEnforcer { _ => _ =>
        Future.successful(
          Ok(startChangeOfAddressView(addrType))
        )
      }
    }

}
