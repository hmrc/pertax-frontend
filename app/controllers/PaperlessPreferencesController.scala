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

import config.ConfigDecorator
import controllers.auth._
import controllers.auth.requests.UserRequest
import com.google.inject.Inject
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import services.partials.PreferencesFrontendPartialService
import uk.gov.hmrc.renderer.{ActiveTabMessages, TemplateRenderer}
import util.LocalPartialRetriever

import scala.concurrent.Future

class PaperlessPreferencesController @Inject()(
  val messagesApi: MessagesApi,
  val preferencesFrontendPartialService: PreferencesFrontendPartialService,
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
          for {
            managePrefsPartial <- preferencesFrontendPartialService.getManagePreferencesPartial(
                                   configDecorator.pertaxFrontendHomeUrl,
                                   Messages("label.back_to_account_home"))
          } yield {
            Ok(views.html.preferences.managePrefs(managePrefsPartial.successfulContentOrEmpty))
          }
        }
    }
}
