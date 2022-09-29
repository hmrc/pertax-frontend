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
import models.{CreatePayment, PaymentRequest}
import play.api.http.Status._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class PayApiConnector @Inject() (http: HttpClient, configDecorator: ConfigDecorator, val metrics: Metrics)
    extends HasMetrics {

  def createPayment(
    request: PaymentRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CreatePayment]] = {
    val postUrl = configDecorator.makeAPaymentUrl

    withMetricsTimer("create-payment") { timer =>
      http.POST[PaymentRequest, HttpResponse](postUrl, request) flatMap { response =>
        response.status match {
          case CREATED =>
            timer.completeTimerAndIncrementSuccessCounter()
            Future.successful(Some(response.json.as[CreatePayment]))
          case _ =>
            timer.completeTimerAndIncrementFailedCounter()
            Future.successful(None)
        }
      }
    }
  }
}
