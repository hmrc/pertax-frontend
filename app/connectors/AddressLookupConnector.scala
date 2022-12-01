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

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import metrics._
import models.addresslookup.{AddressLookup, RecordSet}
import play.api.Logging
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util._

import scala.concurrent.{ExecutionContext, Future}

sealed trait AddressLookupResponse

final case class AddressLookupSuccessResponse(addressList: RecordSet) extends AddressLookupResponse
final case class AddressLookupUnexpectedResponse(r: HttpResponse) extends AddressLookupResponse
final case class AddressLookupErrorResponse(cause: Exception) extends AddressLookupResponse

@Singleton
class AddressLookupConnector @Inject() (
  configDecorator: ConfigDecorator,
  val http: HttpClient,
  val metrics: Metrics,
  val tools: Tools,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
) extends HasMetrics
    with Logging {

  lazy val addressLookupUrl = servicesConfig.baseUrl("address-lookup")

  def lookup(postcode: String, filter: Option[String] = None)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, RecordSet] =
    withMetricsTimer("address-lookup") { t =>
      val pc                 = postcode.replaceAll(" ", "")
      val newHc              = hc.withExtraHeaders("X-Hmrc-Origin" -> configDecorator.origin)
      val addressRequestBody = AddressLookup(pc, filter)

      httpClientResponse
        .read(
          http.POST[AddressLookup, Either[UpstreamErrorResponse, HttpResponse]](
            s"$addressLookupUrl/lookup",
            addressRequestBody
          )(implicitly, implicitly, newHc, implicitly)
        )
        .bimap(
          error => {
            t.completeTimerAndIncrementFailedCounter()
            error
          },
          response => {
            t.completeTimerAndIncrementSuccessCounter()
            RecordSet.fromJsonAddressLookupService(response.json)
          }
        )
    }
}
