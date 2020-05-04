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

package util

import com.google.inject.Inject
import metrics.HasMetrics
import play.api.Logger
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, HttpGet}
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.SessionCookieCrypto
import uk.gov.hmrc.play.partials.HtmlPartial._
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}

import scala.concurrent.{ExecutionContext, Future}

/*
 * This is a PartialRetriever with a HeaderCarrierForPartialsConverter to forward request headers on
 */
abstract class EnhancedPartialRetriever @Inject()(sessionCookieCrypto: SessionCookieCrypto)(
  implicit executionContext: ExecutionContext)
    extends HeaderCarrierForPartialsConverter with HasMetrics {

  def http: HttpGet

  override def crypto: String => String = cookie => cookie

  def loadPartial(url: String)(implicit hc: HeaderCarrier): Future[HtmlPartial] =
    withMetricsTimer("load-partial") { timer =>
      http.GET[HtmlPartial](url) map {
        case partial: HtmlPartial.Success =>
          timer.completeTimerAndIncrementSuccessCounter()
          partial
        case partial: HtmlPartial.Failure =>
          timer.completeTimerAndIncrementFailedCounter()
          partial
      } recover {
        case e =>
          timer.completeTimerAndIncrementFailedCounter()
          Logger.warn(s"Failed to load partial", e)
          e match {
            case ex: HttpException =>
              HtmlPartial.Failure(Some(ex.responseCode))
            case _ =>
              HtmlPartial.Failure(None)
          }
      }

    }
}
