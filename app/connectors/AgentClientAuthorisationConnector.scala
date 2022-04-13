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
import com.google.common.util.concurrent.RateLimiter
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import metrics.{Metrics, MetricsEnumeration}
import models.AgentClientStatus
import play.api.Logging
import play.api.libs.json.Format
import play.api.libs.json.Format.GenericFormat
import play.api.mvc.Request
import repositories.SessionCacheRepository
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpReadsInstances.readEitherOf
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.{Limiters, Throttle, Timeout}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait AgentClientAuthorisationConnector {
  def getAgentClientStatus(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, AgentClientStatus]
}

class CachingAgentClientAuthorisationConnector @Inject() (
  @Named("default") underlying: AgentClientAuthorisationConnector,
  sessionCacheRepository: SessionCacheRepository
)(implicit ec: ExecutionContext)
    extends AgentClientAuthorisationConnector {

  private def cache[L, A: Format](
    key: String
  )(f: => EitherT[Future, L, A])(implicit request: Request[_]): EitherT[Future, L, A] = {

    def fetchAndCache: EitherT[Future, L, A] =
      for {
        result <- f
        _ <- EitherT[Future, L, (String, String)](
               sessionCacheRepository
                 .putSession[A](DataKey[A](key), result)
                 .map(Right(_))
             )
      } yield result

    EitherT(
      sessionCacheRepository
        .getFromSession[A](DataKey[A](key))
        .map {
          case None        => fetchAndCache
          case Some(value) => EitherT.rightT[Future, L](value)
        }
        .map(_.value)
        .flatten
    ) recoverWith { case NonFatal(_) =>
      fetchAndCache
    }
  }

  override def getAgentClientStatus(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, AgentClientStatus] =
    cache("agentClientStatus") {
      underlying.getAgentClientStatus
    }

}

@Singleton
class DefaultAgentClientAuthorisationConnector @Inject() (
  val httpClient: HttpClient,
  val metrics: Metrics,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse,
  limiters: Limiters
) extends AgentClientAuthorisationConnector with Throttle with Timeout with Logging {
  val rateLimiter: RateLimiter = limiters.rateLimiterForGetClientStatus
  lazy val baseUrl = servicesConfig.baseUrl("agent-client-authorisation")
  lazy val timeoutInSec =
    servicesConfig.getInt("feature.agent-client-authorisation.timeoutInSec")

  override def getAgentClientStatus(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, AgentClientStatus] = {
    val timerContext: Timer.Context =
      metrics.startTimer(MetricsEnumeration.GET_AGENT_CLIENT_STATUS)

    val result =
      withThrottle {
        withTimeout(timeoutInSec seconds) {
          httpClient
            .GET[Either[UpstreamErrorResponse, HttpResponse]](s"$baseUrl/agent-client-authorisation/status")
            .map { response =>
              timerContext.stop()
              response
            }
        }
      }
    httpClientResponse.read(result, MetricsEnumeration.GET_AGENT_CLIENT_STATUS).map { response =>
      response.json.as[AgentClientStatus]
    }
  }
}
