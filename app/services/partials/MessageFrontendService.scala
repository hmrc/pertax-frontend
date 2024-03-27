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

package services.partials

import com.google.inject.{Inject, Singleton}
import connectors.EnhancedPartialRetriever
import play.api.Logging
import play.api.mvc.RequestHeader
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.partials.HtmlPartial

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageFrontendService @Inject() (
  servicesConfig: ServicesConfig,
  enhancedPartialRetriever: EnhancedPartialRetriever
)(implicit executionContext: ExecutionContext)
    extends Logging {

  lazy val messageFrontendUrl: String = servicesConfig.baseUrl("message-frontend")

  def getMessageListPartial(implicit request: RequestHeader): Future[HtmlPartial] =
    enhancedPartialRetriever.loadPartial(messageFrontendUrl + "/messages")

  def getMessageDetailPartial(messageToken: String)(implicit request: RequestHeader): Future[HtmlPartial] =
    enhancedPartialRetriever.loadPartial(messageFrontendUrl + "/messages/" + messageToken)

  def getMessageInboxLinkPartial(implicit request: RequestHeader): Future[HtmlPartial] =
    enhancedPartialRetriever.loadPartial(
      messageFrontendUrl + "/messages/inbox-link?messagesInboxUrl=" + controllers.routes.MessageController.messageList
    )

}
