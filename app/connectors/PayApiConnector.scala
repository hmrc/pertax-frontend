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
import models.PaymentRequest
import play.api.http.Status._
import play.api.libs.json.{Json, Reads}
import services.http.WsAllMethods
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

case class CreatePaymentResponse(journeyId: String, nextUrl: String)

object CreatePaymentResponse {
  implicit val format = Json.format[CreatePaymentResponse]
}

class PayApiConnector @Inject()(http: WsAllMethods, configDecorator: ConfigDecorator) {

  def createPayment(
    request: PaymentRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CreatePaymentResponse] = {
    val postUrl = configDecorator.makeAPaymentUrl

    val httpResponse = http.POST[PaymentRequest, HttpResponse](postUrl, request) flatMap { response =>
      response.status match {
        case CREATED => Future.successful(response)
        case _       => Future.failed(new HttpException(response.body, response.status))
      }
    }

    httpResponse.map { response =>
      responseTo[CreatePaymentResponse](response)
    }
  }

  private def responseTo[T](response: HttpResponse)(implicit rds: Reads[T]) = response.json.as[T]
}
