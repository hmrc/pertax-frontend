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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth._
import controllers.auth.requests.UserRequest
import play.api.Mode.Mode
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.renderer.{ActiveTabMessages, TemplateRenderer}
import util.{LocalPartialRetriever, Tools}

import scala.concurrent.Future

class PaperlessPreferencesController @Inject()(
  val messagesApi: MessagesApi,
  environment: Environment,
  configuration: Configuration,
  tools: Tools,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  withBreadcrumbAction: WithBreadcrumbAction)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer)
    extends PertaxBaseController {

  def managePreferences: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withActiveTabAction
      .addActiveTab(ActiveTabMessages) andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
      implicit request: UserRequest[_] =>
        if (request.isVerify) {
          Future.successful(
            BadRequest(
              views.html.error(
                "global.error.BadRequest.title",
                Some("global.error.BadRequest.heading"),
                List("global.error.BadRequest.message"))))
        } else {
          Future.successful(
            Redirect(
              getManagePreferencesUrl(configDecorator.pertaxFrontendHomeUrl, Messages("label.back_to_account_home"))))
        }
    }

  private def getManagePreferencesUrl(returnUrl: String, returnLinkText: String): String =
    s"${configDecorator.preferencesFrontendHost}/paperless/check-settings?returnUrl=${tools.encryptAndEncode(returnUrl)}&returnLinkText=${tools
      .encryptAndEncode(returnLinkText)}"
}
