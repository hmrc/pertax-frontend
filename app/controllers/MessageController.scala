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
import models.Breadcrumb
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import services.partials.MessageFrontendService
import uk.gov.hmrc.play.partials.HtmlPartial
import views.html.message.{MessageDetailView, MessageInboxView}

import scala.concurrent.ExecutionContext

class MessageController @Inject() (
  val messageFrontendService: MessageFrontendService,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  messageInboxView: MessageInboxView,
  messageDetailView: MessageDetailView
)(implicit val configDecorator: ConfigDecorator, val ec: ExecutionContext)
    extends PertaxBaseController(cc) {

  private def messageBreadcrumb: Breadcrumb =
    "label.all_messages" -> routes.MessageController.messageList.url ::
      baseBreadcrumb

  def messageList: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction
      .addBreadcrumb(baseBreadcrumb)).async { implicit request =>
      messageFrontendService.getMessageListPartial map { (p: HtmlPartial) =>
        Ok(
          messageInboxView(
            messageListPartial = p successfulContentOrElse Html(
              Messages("label.sorry_theres_been_a_technical_problem_retrieving_your_messages")
            )
          )
        )
      }
    }

  def messageDetail(messageToken: String): Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction
      .addBreadcrumb(messageBreadcrumb)).async { implicit request =>
      messageFrontendService.getMessageDetailPartial(messageToken).map {
        case HtmlPartial.Success(Some(title), content) =>
          Ok(messageDetailView(message = content, title = title))
        case HtmlPartial.Success(None, content)        =>
          Ok(messageDetailView(message = content, title = Messages("label.message")))
        case HtmlPartial.Failure(_, _)                 =>
          Ok(
            messageDetailView(
              message = Html(Messages("label.sorry_theres_been_a_techinal_problem_retrieving_your_message")),
              title = Messages("label.message")
            )
          )
      }
    }
}
