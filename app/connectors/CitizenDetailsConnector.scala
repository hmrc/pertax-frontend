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
import models._
import play.api.Logging
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CitizenDetailsConnector @Inject() (
  val httpClient: HttpClient,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
) extends Logging {

  lazy val citizenDetailsUrl = servicesConfig.baseUrl("citizen-details")

  def personDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    httpClientResponse
      .read(
        httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](
          s"$citizenDetailsUrl/citizen-details/$nino/designatory-details"
        )
      )

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val body = Json.obj("etag" -> etag, "address" -> Json.toJson(address))
    httpClientResponse
      .read(
        httpClient.POST[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
          s"$citizenDetailsUrl/citizen-details/$nino/designatory-details/address",
          body
        )
      )
  }

  def getMatchingDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    httpClientResponse
      .read(
        httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](s"$citizenDetailsUrl/citizen-details/nino/$nino")
      )

  def getEtag(
    nino: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    httpClientResponse
      .read(
        httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](s"$citizenDetailsUrl/citizen-details/$nino/etag")
      )

}
