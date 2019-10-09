/*
 * Copyright 2019 HM Revenue & Customs
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
import error.RendersErrors
import javax.inject.Inject
import models.Breadcrumb
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import play.twirl.api.Html
import services.partials.MessageFrontendService
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.renderer.{ActiveTabMessages, TemplateRenderer}
import util.LocalPartialRetriever

class MessageController @Inject()(
  val messagesApi: MessagesApi,
  val messageFrontendService: MessageFrontendService,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  withBreadcrumbAction: WithBreadcrumbAction)(
  implicit val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val templateRenderer: TemplateRenderer)
    extends PertaxBaseController with RendersErrors {

  def messageBreadcrumb: Breadcrumb =
    "label.all_messages" -> routes.MessageController.messageList().url ::
      baseBreadcrumb

  def messageList: Action[AnyContent] =
    (authJourney.auth andThen withActiveTabAction.addActiveTab(ActiveTabMessages) andThen withBreadcrumbAction
      .addBreadcrumb(baseBreadcrumb)).async { implicit request =>
      if (request.isGovernmentGateway) {
        messageFrontendService.getMessageListPartial map { p =>
          Ok(
            views.html.message.messageInbox(messageListPartial = p successfulContentOrElse Html(
              Messages("label.sorry_theres_been_a_technical_problem_retrieving_your_messages"))))
        }
      } else {
        futureError(UNAUTHORIZED)
      }
    }

  def messageDetail(messageToken: String): Action[AnyContent] =
    (authJourney.auth andThen withActiveTabAction.addActiveTab(ActiveTabMessages) andThen withBreadcrumbAction
      .addBreadcrumb(messageBreadcrumb)).async { implicit request =>
      if (request.isGovernmentGateway) {
        messageFrontendService.getMessageDetailPartial(messageToken).map {
          case HtmlPartial.Success(Some(title), content) =>
            Ok(views.html.message.messageDetail(message = content, title = title))
          case HtmlPartial.Success(None, content) =>
            Ok(views.html.message.messageDetail(message = content, title = Messages("label.message")))
          case HtmlPartial.Failure(_, _) =>
            Ok(
              views.html.message.messageDetail(
                message = Html(Messages("label.sorry_theres_been_a_techinal_problem_retrieving_your_message")),
                title = Messages("label.message")))
        }
      } else {
        futureError(UNAUTHORIZED)
      }
    }
}
