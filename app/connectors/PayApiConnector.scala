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

import java.time.LocalDateTime

import com.google.inject.Inject
import config.ConfigDecorator
import models.PaymentRequest
import play.api.http.Status._
import play.api.libs.json.Json
import services.http.WsAllMethods
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, Upstream5xxResponse}

import scala.concurrent.{ExecutionContext, Future}

final case class CreatePayment(journeyId: String, nextUrl: String)

object CreatePayment {
  implicit val format = Json.format[CreatePayment]
}

final case class PayApiPayment(status: String, amountInPence: Int, reference: String, createdOn: LocalDateTime)

object PayApiPayment {
  implicit val format = Json.format[PayApiPayment]
}

final case class PaymentSearchResult(searchScope: String, searchTag: String, payments: List[PayApiPayment])

object PaymentSearchResult {
  implicit val format = Json.format[PaymentSearchResult]
}

class PayApiConnector @Inject()(http: WsAllMethods, configDecorator: ConfigDecorator) {

  def createPayment(
    request: PaymentRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CreatePayment]] = {
    val postUrl = configDecorator.makeAPaymentUrl

    http.POST[PaymentRequest, HttpResponse](postUrl, request) flatMap { response =>
      response.status match {
        case CREATED =>
          Future.successful(Some(response.json.as[CreatePayment]))
        case _ => Future.successful(None)
      }
    }
  }

  def findPayments(
    utr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PaymentSearchResult]] = {
    val url = s"${configDecorator.getPaymentsUrl}/$utr"

    http.GET(url) flatMap { response =>
      response.status match {
        case OK =>
          Future.successful(Some(response.json.as[PaymentSearchResult]))
        case NOT_FOUND =>
          Future.successful(None)
        case error =>
          Future.failed(Upstream5xxResponse(s"findPayments returned $error", error, INTERNAL_SERVER_ERROR))
      }
    }
  }
}
