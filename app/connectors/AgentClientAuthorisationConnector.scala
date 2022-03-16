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
import cats.implicits._
import com.codahale.metrics.Timer
import com.google.inject.{Inject, Singleton}
import metrics.{HasMetrics, Metrics, MetricsEnumeration}
import models.{AgentClientStatus, PersonDetails}
import play.api.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpReadsInstances.readEitherOf
import util.{RateLimitedException, Throttle}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentClientAuthorisationConnector @Inject() (
  val httpClient: HttpClient,
  val metrics: Metrics,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
) extends Throttle with Logging {

  lazy val agentClientAuthorisationUrl = servicesConfig.baseUrl("agent-client-authorisation")
  lazy val agentClientAuthorisationRateLimit =
    servicesConfig.getConfInt("feature.agent-client-authorisation.rateLimit", 10000).toDouble

  def getAgentClientStatus(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, AgentClientStatus] = {
    val timerContext: Timer.Context =
      metrics.startTimer(MetricsEnumeration.GET_AGENT_CLIENT_STATUS)

    val result = throttle(agentClientAuthorisationRateLimit) {
      httpClient
        .GET[Either[UpstreamErrorResponse, HttpResponse]](s"$agentClientAuthorisationUrl/status")
        .map { response =>
          timerContext.stop()
          response
        }
    }
    httpClientResponse.read(result, MetricsEnumeration.GET_AGENT_CLIENT_STATUS).map { response =>
      response.json.as[AgentClientStatus]
    }
  }
}
