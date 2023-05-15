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
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentsConnector @Inject() (
  http: HttpClient,
  configDecorator: ConfigDecorator,
  httpClientResponse: HttpClientResponse
) {

  val baseUrl = configDecorator.enrolmentStoreProxyUrl

  def getUserIdsWithEnrolments(
    saUtr: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, Seq[String]] = {
    val url = s"$baseUrl/enrolment-store/enrolments/IR-SA~UTR~$saUtr/users"
    httpClientResponse
      .read(
        http.GET[Either[UpstreamErrorResponse, HttpResponse]](url)
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
}
