/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import models.MessageCount
import play.api.Logger
import play.api.mvc.RequestHeader
import services.http.WsAllMethods
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.partials.HtmlPartial
import util.EnhancedPartialRetriever
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future


@Singleton
class MessageFrontendService @Inject()(override val http: WsAllMethods, val metrics: Metrics) extends EnhancedPartialRetriever with HasMetrics with ServicesConfig {


  lazy val messageFrontendUrl: String = baseUrl("message-frontend")

  def getMessageListPartial(implicit request: RequestHeader): Future[HtmlPartial] = {
    loadPartial(messageFrontendUrl + "/messages")
  }

  def getMessageDetailPartial(messageToken: String)(implicit request: RequestHeader): Future[HtmlPartial] = {
    loadPartial(messageFrontendUrl + "/messages/" + messageToken)
  }

  def getMessageInboxLinkPartial(implicit request: RequestHeader): Future[HtmlPartial] = {
    loadPartial(messageFrontendUrl + "/messages/inbox-link?messagesInboxUrl=" + controllers.routes.MessageController.messageList())
  }



  def getUnreadMessageCount(implicit request: RequestHeader): Future[Option[Int]] =
    loadJson(messageFrontendUrl + "/messages/count?read=No").map(_.map(_.count))


  private def loadJson(url: String)(implicit hc: HeaderCarrier): Future[Option[MessageCount]] = {

    withMetricsTimer("load-json") { t =>

      http.GET[Option[MessageCount]](url) recover {
        case e =>
          t.completeTimerAndIncrementFailedCounter()
          Logger.warn(s"Failed to load json", e)
          None
      }

    }
  }
}


