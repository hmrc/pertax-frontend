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
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import models._
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CitizenDetailsConnector @Inject() (
  val httpClientV2: HttpClientV2,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse,
  configDecorator: ConfigDecorator
) extends Logging {

  private lazy val citizenDetailsUrl: String = servicesConfig.baseUrl("citizen-details")

  private lazy val timeoutInMilliseconds: Int = configDecorator.citizenDetailsTimeoutInMilliseconds

  def personDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val url                                                              = s"$citizenDetailsUrl/citizen-details/$nino/designatory-details"
    val apiResponse: Future[Either[UpstreamErrorResponse, HttpResponse]] = httpClientV2
      .get(url"$url")
      .transform(_.withRequestTimeout(timeoutInMilliseconds.milliseconds))
      .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
    httpClientResponse.read(apiResponse)
  }

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext
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

  def getEtag(
    nino: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {

    val url = s"$citizenDetailsUrl/citizen-details/$nino/etag"
    httpClientResponse.read(
      httpClientV2
        .get(url"$url")
        .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
    )
  }
}
