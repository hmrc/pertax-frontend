/*
 * Copyright 2024 HM Revenue & Customs
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
import cats.implicits.*
import com.google.common.util.concurrent.RateLimiter
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import models.AgentClientStatus
import play.api.Logging
import play.api.libs.json.Format
import play.api.libs.json.Format.GenericFormat
import play.api.mvc.Request
import services.CacheService
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.{FutureEarlyTimeout, Limiters, Throttle}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

trait AgentClientAuthorisationConnector {
  def getAgentClientStatus(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, AgentClientStatus]
}

class CachingAgentClientAuthorisationConnector @Inject() (
  @Named("default") underlying: AgentClientAuthorisationConnector,
  cacheService: CacheService
) extends AgentClientAuthorisationConnector {

  override def getAgentClientStatus(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, AgentClientStatus] =
    cacheService.cache("agentClientStatus") { () =>
      underlying.getAgentClientStatus
    }

}

@Singleton
class DefaultAgentClientAuthorisationConnector @Inject() (
  val httpClientV2: HttpClientV2,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse,
  limiters: Limiters
) extends AgentClientAuthorisationConnector
    with Throttle
    with Logging {
  val rateLimiter: RateLimiter = limiters.rateLimiterForGetClientStatus
  lazy val baseUrl: String     = servicesConfig.baseUrl("agent-client-relationships")
  lazy val timeoutInSec: Int   =
    servicesConfig.getInt("feature.agent-client-relationships.timeoutInSec")

  override def getAgentClientStatus(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, AgentClientStatus] = {
    val url    = s"$baseUrl/agent-client-relationships/customer-status"
    val result =
      withThrottle {
        httpClientV2
          .get(url"$url")
          .transform(_.withRequestTimeout(timeoutInSec.seconds))
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
          .recoverWith { case exception: GatewayTimeoutException =>
            logger.error(exception.message)
            Future.failed(FutureEarlyTimeout)
          }
      }
    httpClientResponse.read(result).map { response =>
      response.json.as[AgentClientStatus]
    }
  }
}
