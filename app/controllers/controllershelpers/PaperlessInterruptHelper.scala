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

package controllers.controllershelpers

import com.google.inject.Inject
import config.ConfigDecorator
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models.admin.PaperlessInterruptToggle
import play.api.mvc.Result
import play.api.mvc.Results._
import services.admin.FeatureFlagService
import uk.gov.hmrc.http.HttpReads.is2xx

import scala.concurrent.{ExecutionContext, Future}

class PaperlessInterruptHelper @Inject() (
  preferencesFrontendConnector: PreferencesFrontendConnector,
  featureFlagService: FeatureFlagService
) {

  def enforcePaperlessPreference(
    block: => Future[Result]
  )(implicit request: UserRequest[_], configDecorator: ConfigDecorator, ec: ExecutionContext): Future[Result] =
    featureFlagService.get(PaperlessInterruptToggle).flatMap { featureFlag =>
      if (featureFlag.isEnabled) {
        preferencesFrontendConnector
          .getPaperlessPreference()
          .foldF(
            _ => block,
            response =>
              if (is2xx(response.status)) block
              else Future.successful(Redirect((response.json \ "redirectUserTo").as[String]))
          )
      } else {
        block
      }
    }
}
