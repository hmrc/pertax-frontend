/*
 * Copyright 2021 HM Revenue & Customs
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
import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import models.MessageCount
import play.api.Logger
import play.api.mvc.RequestHeader
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.SessionCookieCrypto
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.partials.HtmlPartial
import util.EnhancedPartialRetriever

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageFrontendService @Inject()(
  override val http: HttpClient,
  val metrics: Metrics,
  val sessionCookieCrypto: SessionCookieCrypto,
  servicesConfig: ServicesConfig)(implicit executionContext: ExecutionContext)
    extends EnhancedPartialRetriever(sessionCookieCrypto) with HasMetrics {

  lazy val messageFrontendUrl: String = servicesConfig.baseUrl("message-frontend")

  def getMessageListPartial(implicit request: RequestHeader): Future[HtmlPartial] =
    loadPartial(messageFrontendUrl + "/messages")

  def getMessageDetailPartial(messageToken: String)(implicit request: RequestHeader): Future[HtmlPartial] =
    loadPartial(messageFrontendUrl + "/messages/" + messageToken)

  def getMessageInboxLinkPartial(implicit request: RequestHeader): Future[HtmlPartial] =
    loadPartial(
      messageFrontendUrl + "/messages/inbox-link?messagesInboxUrl=" + controllers.routes.MessageController
        .messageList())

  def getUnreadMessageCount(implicit request: RequestHeader): Future[Option[Int]] = {
    val url = messageFrontendUrl + "/messages/count?read=No"

    withMetricsTimer("get-unread-message-count") { t =>
      (for {
        messageCount <- http.GET[Option[MessageCount]](url)
      } yield {
        t.completeTimerAndIncrementSuccessCounter()
        messageCount.map(_.count)
      }) recover {
        case e =>
          t.completeTimerAndIncrementFailedCounter()
          Logger.warn(s"Failed to load json", e)
          None
      }
    }
  }
}
