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

package connectors

import cats.data.EitherT
import cats.implicits.*
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import models.Address
import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc.Request
import play.api.{Logger, Logging}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import services.CacheService

import javax.inject.Named
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

trait CitizenDetailsConnector {
  def personDetails(
    nino: Nino,
    refreshCache: Boolean = false
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, JsValue]

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Boolean]

  def getMatchingDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, JsValue]
}

@Singleton
class CachingCitizenDetailsConnector @Inject() (
  @Named("default") underlying: CitizenDetailsConnector,
  cacheService: CacheService
) extends CitizenDetailsConnector
    with Logging {

  def cacheKey(nino: Nino) = s"getPersonDetails-$nino"

  def personDetails(nino: Nino, refreshCache: Boolean = false)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, JsValue] =
    if (refreshCache) {
      EitherT.liftF(cacheService.deleteFromCache(cacheKey(nino))).flatMap { _ =>
        cacheService.cache(cacheKey(nino)) {
          underlying.personDetails(nino: Nino, refreshCache)
        }
      }
    } else {
      cacheService.cache(cacheKey(nino)) {
        underlying.personDetails(nino: Nino, refreshCache)
      }
    }

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Boolean] =
    for {
      update <- underlying.updateAddress(nino, etag, address)
      _      <- cacheService.deleteFromCacheAsEitherT(cacheKey(nino))
    } yield update

  def getMatchingDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, JsValue] =
    underlying.getMatchingDetails(nino)
}

@Singleton
class DefaultCitizenDetailsConnector @Inject() (
  httpClientV2: HttpClientV2,
  configDecorator: ConfigDecorator,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
) extends CitizenDetailsConnector {
  val logger: Logger = Logger(this.getClass)

  private lazy val citizenDetailsUrl: String = servicesConfig.baseUrl("citizen-details")

  def personDetails(
    nino: Nino,
    refreshCache: Boolean = false
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, JsValue] = {
    val url                                                              = s"$citizenDetailsUrl/citizen-details/$nino/designatory-details?cached=${!refreshCache}"
    val apiResponse: Future[Either[UpstreamErrorResponse, HttpResponse]] = httpClientV2
      .get(url"$url")
      .transform(_.withRequestTimeout(configDecorator.citizenDetailsTimeoutInMilliseconds.milliseconds))
      .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
    httpClientResponse.read(apiResponse).map(_.json)
  }

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Boolean] = {
    val body = Json.obj("etag" -> etag, "address" -> Json.toJson(address))
    val url  = s"$citizenDetailsUrl/citizen-details/$nino/designatory-details/address"
    httpClientResponse
      .readUpdateAddress(
        httpClientV2
          .post(url"$url")
          .withBody(body)
          .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
      )
      .map(_ => true)
  }

  def getMatchingDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, JsValue] = {
    val url = s"$citizenDetailsUrl/citizen-details/nino/$nino"
    httpClientResponse
      .read(
        httpClientV2
          .get(url"$url")
          .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
      )
      .map(_.json)
  }
}
