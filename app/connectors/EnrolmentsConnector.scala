/*
 * Copyright 2019 HM Revenue & Customs
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

import com.google.inject.Inject
import config.ConfigDecorator
import play.api.Logger
import play.api.http.Status._
import play.api.mvc.Result
import services.http.WsAllMethods
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentsConnector @Inject()(http: WsAllMethods, configDecorator: ConfigDecorator) {

  val baseUrl = configDecorator.enrolmentStoreProxyUrl

  def getUserIdsWithEnrolments(
    saUtr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, Seq[String]]] = {
    val url = s"$baseUrl/enrolment-store/enrolments/IR-SA~UTR~$saUtr/users?type=principal"

    http.GET[HttpResponse](url) map { response =>
      response.status match {
        case OK         => Right((response.json \ "principalUserIds").as[Seq[String]])
        case NO_CONTENT => Right(Seq.empty)
        case errorCode  => Left(s"HttpError: $errorCode. Invalid call for getUserIdsWithEnrolments: $response")
      }
    }
  }
}
