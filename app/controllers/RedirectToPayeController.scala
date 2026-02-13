/*
 * Copyright 2025 HM Revenue & Customs
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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.admin.PayeToPegaRedirectToggle
import play.api.mvc.*
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.ExecutionContext

class RedirectToPayeController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  featureFlagService: FeatureFlagService
)(implicit configDecorator: ConfigDecorator, val ec: ExecutionContext)
    extends PertaxBaseController(cc) {

  def redirectToPaye: Action[AnyContent] =
    authJourney.authWithPersonalDetails.async { implicit request: UserRequest[AnyContent] =>
      featureFlagService.get(PayeToPegaRedirectToggle).map { usePegaRoutingToggle =>
        val penultimateDigit = request.authNino.nino.charAt(6).asDigit
        val destinationUrl   =
          if (
            usePegaRoutingToggle.isEnabled && configDecorator.payeToPegaRedirectList.contains(penultimateDigit) &&
            request.trustedHelper.isEmpty
          ) {
            configDecorator.payeToPegaRedirectUrl
          } else {
            s"${configDecorator.taiHost}/check-income-tax/what-do-you-want-to-do"
          }

        Redirect(destinationUrl)
      }
    }
}
