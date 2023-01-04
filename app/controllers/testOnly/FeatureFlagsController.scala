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

package controllers.testOnly

import controllers.PertaxBaseController
import models.admin.FeatureFlagName
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.admin.FeatureFlagService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class FeatureFlagsController @Inject() (
  cc: MessagesControllerComponents,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext)
    extends PertaxBaseController(cc) {

  def setFlag(featureFlagName: FeatureFlagName, isEnabled: Boolean): Action[AnyContent] = Action.async { request =>
    featureFlagService.set(featureFlagName, isEnabled).map {
      case true  => Ok(s"Flag $featureFlagName set to $isEnabled")
      case false => InternalServerError(s"Error while setting flag $featureFlagName to $isEnabled")
    }
  }
}
