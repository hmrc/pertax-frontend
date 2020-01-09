/*
 * Copyright 2020 HM Revenue & Customs
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

package models

import java.time.LocalDateTime

import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsError, JsPath, JsSuccess, Json, Reads}
import uk.gov.hmrc.http.{HttpReads, HttpResponse, Upstream5xxResponse}
import play.api.libs.functional.syntax._

final case class CreatePayment(journeyId: String, nextUrl: String)

object CreatePayment {
  implicit val format = Json.format[CreatePayment]
}

final case class PayApiPayment(status: String, amountInPence: Option[Int], reference: String, createdOn: LocalDateTime)

object PayApiPayment {
  implicit val writes = Json.writes[PayApiPayment]

  implicit val reads: Reads[PayApiPayment] = (
    (JsPath \ "status").read[String] and
      (JsPath \ "amountInPence").readNullable[Int] and
      (JsPath \ "reference").read[String] and
      (JsPath \ "createdOn").read[LocalDateTime]
  )(PayApiPayment.apply _)
}

final case class PaymentSearchResult(searchScope: String, searchTag: String, payments: List[PayApiPayment])

object PaymentSearchResult {
  implicit val format = Json.format[PaymentSearchResult]

  implicit val httpReads: HttpReads[Option[PaymentSearchResult]] = new HttpReads[Option[PaymentSearchResult]] {
    override def read(method: String, url: String, response: HttpResponse): Option[PaymentSearchResult] =
      response.status match {
        case OK =>
          response.json.validate[PaymentSearchResult] match {
            case JsSuccess(value, _) => Some(value)
            case JsError(error) =>
              val message = s"Unable to parse json as PaymentSearchResult: $error"
              Logger.error(message)
              throw InvalidJsonException(message)
          }
        case NOT_FOUND =>
          None
        case error =>
          val message = s"findPayments returned $error from pay-api"
          Logger.error(message)
          throw Upstream5xxResponse(message, error, INTERNAL_SERVER_ERROR)
      }
  }
}

case class InvalidJsonException(message: String) extends Exception(message)
