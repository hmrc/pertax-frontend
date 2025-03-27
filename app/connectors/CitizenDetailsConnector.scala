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
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import models.Address
import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc.Request
import play.api.{Logger, Logging}
import repositories.SessionCacheRepository
import services.SensitiveFormatService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Named
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

trait CitizenDetailsConnector {
  def personDetails(
    nino: Nino
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, JsValue]

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse]

  def getMatchingDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse]
}

@Singleton
class CachingCitizenDetailsConnector @Inject() (
  @Named("default") underlying: CitizenDetailsConnector,
  sensitiveFormatService: SensitiveFormatService,
  sessionCacheRepository: SessionCacheRepository
)(implicit ec: ExecutionContext)
    extends CitizenDetailsConnector
    with Logging {

  def cache[L, A: Format](
    key: String
  )(f: => EitherT[Future, L, A])(implicit request: Request[_]): EitherT[Future, L, A] = {

    def fetchAndCache: EitherT[Future, L, A] =
      for {
        result <- f
        _      <- EitherT[Future, L, (String, String)](
                    sessionCacheRepository
                      .putSession[A](DataKey[A](key), result)
                      .map(Right(_))
                  )
      } yield result

    def readAndUpdate: EitherT[Future, L, A] =
      EitherT(
        sessionCacheRepository
          .getFromSession[A](DataKey[A](key))
          .flatMap {
            case None        =>
              fetchAndCache.value
            case Some(value) =>
              Future.successful(Right(value))
          }
      )

    readAndUpdate
  }

  def personDetails(nino: Nino)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, JsValue] =
    cache(s"getPersonDetails-$nino") {
      underlying.personDetails(nino: Nino)
    }(sensitiveFormatService.sensitiveFormatFromReadsWrites[JsValue], request)

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    for {
      update <- underlying.updateAddress(nino, etag, address)
      _      <- sessionCacheRepository.deleteFromSessionEitherT(DataKey[JsValue](s"getPersonDetails-$nino"))
    } yield update

  def getMatchingDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    underlying.getMatchingDetails(nino)

  def clearPersonDetailsCache(nino: Nino)(implicit request: Request[_]): Future[Unit] = {
    val cacheKey = s"getPersonDetails-$nino"
    sessionCacheRepository.deleteFromSession(DataKey[JsValue](cacheKey)).map(_ => ())
  }
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
    nino: Nino
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, JsValue] = {
    val url                                                              = s"$citizenDetailsUrl/citizen-details/$nino/designatory-details"
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
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val body = Json.obj("etag" -> etag, "address" -> Json.toJson(address))
    val url  = s"$citizenDetailsUrl/citizen-details/$nino/designatory-details/address"
    httpClientResponse.read(
      httpClientV2
        .post(url"$url")
        .withBody(body)
        .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
    )
  }

  def getMatchingDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val url = s"$citizenDetailsUrl/citizen-details/nino/$nino"
    httpClientResponse.read(
      httpClientV2
        .get(url"$url")
        .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
    )
  }
}
