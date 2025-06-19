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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth._
import controllers.auth.requests.UserRequest
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import util.Tools

import scala.concurrent.Future

class PaperlessPreferencesController @Inject() (
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  tools: Tools
)(implicit configDecorator: ConfigDecorator)
    extends PertaxBaseController(cc) {

  def managePreferences: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
      implicit request: UserRequest[_] =>
        Future.successful(
          Redirect(
            getManagePreferencesUrl(configDecorator.pertaxFrontendHomeUrl, Messages("label.back_to_account_home"))
          )
        )
    }

  private def getManagePreferencesUrl(returnUrl: String, returnLinkText: String): String =
    s"${configDecorator.preferencesFrontendService}/paperless/check-settings?returnUrl=${tools.encryptAndEncode(returnUrl)}&returnLinkText=${tools
        .encryptAndEncode(returnLinkText)}"
}
