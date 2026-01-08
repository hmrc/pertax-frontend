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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.JourneyCacheRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext

class SessionManagementController @Inject() (
  authJourney: AuthJourney,
  mcc: MessagesControllerComponents,
  journeyCacheRepository: JourneyCacheRepository
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends FrontendController(mcc) {

  def keepAlive: Action[AnyContent] = authJourney.authWithPersonalDetails.async { implicit request =>
    journeyCacheRepository.keepAlive.map { _ =>
      Ok("")
    }
  }

  def timeOut: Action[AnyContent] = Action.async { implicit request =>
    journeyCacheRepository.clear.map { _ =>
      Redirect(
        configDecorator.getBasGatewayFrontendSignOutUrl(
          configDecorator.getFeedbackSurveyUrl(configDecorator.defaultOrigin)
        )
      )
    }
  }
}
