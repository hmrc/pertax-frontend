/*
 * Copyright 2025 HM Revenue & Customs
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
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import play.api.Logging
import play.api.libs.json.{Format, JsValue}
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import services.CacheService

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

trait TaiConnector {
  def taxComponents(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, JsValue]
}

@Singleton
class DefaultTaiConnector @Inject() (
  val httpClientV2: HttpClientV2,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse,
  configDecorator: ConfigDecorator
) extends TaiConnector
    with Logging {

  private lazy val taiUrl = servicesConfig.baseUrl("tai")

  override def taxComponents(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, JsValue] = {
    val url = s"$taiUrl/tai/$nino/tax-account/$year/tax-components"
    httpClientResponse
      .read(
        httpClientV2
          .get(url"$url")
          .transform(_.withRequestTimeout(configDecorator.taiTimeoutInMilliseconds.milliseconds))
          .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
      )
      .map(_.json)
  }
}

@Singleton
class CachingTaiConnector @Inject() (
  @Named("default") underlying: TaiConnector,
  cacheService: CacheService
) extends TaiConnector
    with Logging {

  override def taxComponents(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, JsValue] = {
    val key = s"taxComponents.${nino.value}.$year"
    cacheService.cache[UpstreamErrorResponse, JsValue](key) { () =>
      underlying.taxComponents(nino, year)
    }
  }
}
