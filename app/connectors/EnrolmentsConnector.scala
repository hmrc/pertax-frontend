/*
 * Copyright 2022 HM Revenue & Customs
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
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import metrics.HasMetrics
import play.api.http.Status._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentsConnector @Inject() (http: HttpClient, configDecorator: ConfigDecorator, val metrics: Metrics)
    extends HasMetrics {

  val baseUrl = configDecorator.enrolmentStoreProxyUrl

  def getUserIdsWithEnrolments(
    saUtr: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, Seq[String]]] = {
    val url = s"$baseUrl/enrolment-store/enrolments/IR-SA~UTR~$saUtr/users"

    withMetricsTimer("get-user-ids-with-enrolments") { timer =>
      http.GET[HttpResponse](url) map { response =>
        response.status match {
          case OK         =>
            timer.completeTimerAndIncrementSuccessCounter()
            Right((response.json \ "principalUserIds").as[Seq[String]])
          case NO_CONTENT =>
            timer.completeTimerAndIncrementSuccessCounter()
            Right(Seq.empty)
          case errorCode  =>
            timer.completeTimerAndIncrementFailedCounter()
            Left(s"HttpError: $errorCode. Invalid call for getUserIdsWithEnrolments: $response")
        }
      }
    }
  }
}
