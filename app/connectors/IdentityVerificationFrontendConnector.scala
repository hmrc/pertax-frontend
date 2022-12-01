/*
 * Copyright 2022 HM Revenue & Customs
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

package connectors

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import play.api.Logging
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IdentityVerificationFrontendConnector @Inject() (
  val httpClient: HttpClient,
  val metrics: Metrics,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
) extends HasMetrics
    with Logging {

  lazy val identityVerificationFrontendUrl: String = servicesConfig.baseUrl("identity-verification-frontend")

  def getIVJourneyStatus(
    journeyId: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    withMetricsTimer("get-iv-journey-status") { t =>
      httpClientResponse
        .read(
          httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](
            s"$identityVerificationFrontendUrl/mdtp/journey/journeyId/$journeyId"
          )
        )
        .bimap(
          error => {
            t.completeTimerAndIncrementFailedCounter()
            error
          },
          response => {
            t.completeTimerAndIncrementSuccessCounter()
            response
          }
        )
    }
}
