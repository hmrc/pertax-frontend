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
import com.google.inject.Inject
import config.ConfigDecorator
import models.enrolments.EnrolmentEnum.IRSAKey
import models.enrolments.{KnownFactQueryForNINO, KnownFactResponseForNINO}
import play.api.http.Status._
import play.api.libs.Files.logger
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

class EnrolmentsConnector @Inject() (
  httpv2: HttpClientV2,
  configDecorator: ConfigDecorator,
  httpClientResponse: HttpClientResponse
) {

  val baseUrl: String = configDecorator.enrolmentStoreProxyUrl

  def getUserIdsWithEnrolments(
    enrolmentIdentifier: String,
    enrolmentValue: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, Seq[String]] = {
    val url = s"$baseUrl/enrolment-store/enrolments/$enrolmentIdentifier~$enrolmentValue/users"
    httpClientResponse
      .read(
        httpv2
          .get(url"$url")
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map { response =>
        response.status match {
          case OK =>
            (response.json \ "principalUserIds").as[Seq[String]]
          case _  =>
            Seq.empty
        }
      }
  }

  def getKnownFacts(nino: Nino)(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, Option[KnownFactResponseForNINO]] = {
    val requestBody = KnownFactQueryForNINO.apply(nino, IRSAKey.toString)
    httpClientResponse
      .read(
        httpv2
          .post(url"${configDecorator.enrolmentStoreProxyUrl}/enrolment-store/enrolments")
          .withBody(Json.toJson(requestBody))
          .transform(_.withRequestTimeout(configDecorator.enrolmentStoreProxyTimeoutInMilliseconds.milliseconds))
          .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
      )
      .map(httpResponse =>
        httpResponse.status match {
          case OK         =>
            Some(httpResponse.json.as[KnownFactResponseForNINO])
          case NO_CONTENT => None
          case status     =>
            logger.error(
              s"EACD returned status of $status when searching for users with $IRSAKey enrolment for NINO ${nino.nino}." +
                s"\nError Message: ${httpResponse.body}"
            )
            None
        }
      )
  }
}
