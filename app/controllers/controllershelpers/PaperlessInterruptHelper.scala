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

package controllers.controllershelpers

import config.ConfigDecorator
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models.ActivatePaperlessRequiresUserActionResponse
import play.api.http.Status.PRECONDITION_FAILED
import play.api.mvc.Result
import play.api.mvc.Results._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PaperlessInterruptHelper {

  def preferencesFrontendService: PreferencesFrontendConnector

  def enforcePaperlessPreference(
    block: => Future[Result]
  )(implicit request: UserRequest[_], configDecorator: ConfigDecorator): Future[Result] =
    if (configDecorator.enforcePaperlessPreferenceEnabled) {
      preferencesFrontendService
        .getPaperlessPreference()
        .foldF(
          _ => block,
          redirectFuture => redirectFuture.map(redirectUrl => Future.successful(Redirect(redirectUrl))).getOrElse(block)
        )
    } else {
      block
    }
}
