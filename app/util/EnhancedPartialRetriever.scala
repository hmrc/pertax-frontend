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

package util

import javax.inject.Inject
import metrics.HasMetrics
import play.api.Logger
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.frontend.filters.SessionCookieCryptoFilter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.partials.HtmlPartial._
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}

import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, HttpGet}

/*
 * This is a PartialRetriever with a HeaderCarrierForPartialsConverter to forward request headers on
 */
abstract class EnhancedPartialRetriever @Inject()(applicationCrypto: ApplicationCrypto)
    extends HeaderCarrierForPartialsConverter with HasMetrics {

  def http: HttpGet

  val sessionCookieCryptoFilter: SessionCookieCryptoFilter = new SessionCookieCryptoFilter(applicationCrypto)

  override def crypto = sessionCookieCryptoFilter.encrypt

  def loadPartial(url: String)(implicit hc: HeaderCarrier): Future[HtmlPartial] =
    withMetricsTimer("load-partial") { t =>
      http.GET[HtmlPartial](url) map {
        case p: HtmlPartial.Success =>
          t.completeTimerAndIncrementSuccessCounter()
          p
        case p: HtmlPartial.Failure =>
          t.completeTimerAndIncrementFailedCounter()
          p
      } recover {
        case e =>
          t.completeTimerAndIncrementFailedCounter()
          Logger.warn(s"Failed to load partial", e)
          e match {
            case ex: HttpException =>
              HtmlPartial.Failure(Some(ex.responseCode))
            case ex =>
              HtmlPartial.Failure(None)
          }
      }

    }
}
