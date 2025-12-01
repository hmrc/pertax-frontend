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
import models.enrolments.{KnownFactsRequest, KnownFactsResponse}
import play.api.Logging
import play.api.http.Status.*
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.*
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.mvc.Request
import services.CacheService

import javax.inject.Named
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}

trait EnrolmentsConnector {

  def getUserIdsWithEnrolments(
    enrolmentKey: String
  )(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[String]]

  def getKnownFacts(
    knownFactsRequest: KnownFactsRequest
  )(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, KnownFactsResponse]
}

@Singleton
class CachingEnrolmentsConnector @Inject() (
  @Named("default") underlying: EnrolmentsConnector,
  cacheService: CacheService
) extends EnrolmentsConnector
    with Logging {

  private def knownFactsCacheKey(knownFactsRequest: KnownFactsRequest) =
    s"""getKnownFacts-${knownFactsRequest.service}-${knownFactsRequest.knownFacts
        .map(x => s"${x.key}+${x.value}")
        .mkString("-")}"""

  def getKnownFacts(
    knownFactsRequest: KnownFactsRequest
  )(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, KnownFactsResponse] =
    cacheService.cache(knownFactsCacheKey(knownFactsRequest)) {
      underlying.getKnownFacts(knownFactsRequest)
    }

  def getUserIdsWithEnrolments(
    enrolmentKey: String
  )(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[String]] =
    cacheService.cache(s"credentials-$enrolmentKey") {
      underlying.getUserIdsWithEnrolments(enrolmentKey)
    }
}

class DefaultEnrolmentsConnector @Inject() (
  httpv2: HttpClientV2,
  configDecorator: ConfigDecorator,
  httpClientResponse: HttpClientResponse
)(implicit ec: ExecutionContext)
    extends EnrolmentsConnector {

  val baseUrl: String = configDecorator.enrolmentStoreProxyUrl

  // ES0 - Query users who have an assigned enrolment
  def getUserIdsWithEnrolments(
    enrolmentKey: String
  )(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[String]] = {
    val url = s"$baseUrl/enrolment-store/enrolments/$enrolmentKey/users"
    httpClientResponse
      .read(
        httpv2
          .get(url"$url")
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map { response =>
        response.status match {
          case NO_CONTENT => Seq.empty
          case _          =>
            (response.json \ "principalUserIds").as[Seq[String]]
        }
      }
  }

  // ES20 - Query known facts by verifiers/identifiers
  def getKnownFacts(
    knownFactsRequest: KnownFactsRequest
  )(implicit
    headerCarrier: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, KnownFactsResponse] =
    httpClientResponse
      .read(
        httpv2
          .post(url"${configDecorator.enrolmentStoreProxyUrl}/enrolment-store/enrolments")
          .withBody(Json.toJson(knownFactsRequest))
          .transform(_.withRequestTimeout(configDecorator.enrolmentStoreProxyTimeoutInMilliseconds.milliseconds))
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map(httpResponse =>
        httpResponse.status match {
          case NO_CONTENT => KnownFactsResponse(knownFactsRequest.service, List.empty)
          case _          =>
            httpResponse.json.as[KnownFactsResponse]
        }
      )
}
