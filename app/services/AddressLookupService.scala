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

package services

import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import com.google.inject.{Inject, Singleton}
import metrics._
import models.addresslookup.RecordSet
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Logger}
import services.http.SimpleHttp
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util._

import scala.concurrent.Future

sealed trait AddressLookupResponse

final case class AddressLookupSuccessResponse(addressList: RecordSet) extends AddressLookupResponse
final case class AddressLookupUnexpectedResponse(r: HttpResponse) extends AddressLookupResponse
final case class AddressLookupErrorResponse(cause: Exception) extends AddressLookupResponse

@Singleton
class AddressLookupService @Inject()(
  environment: Environment,
  configuration: Configuration,
  configDecorator: ConfigDecorator,
  val simpleHttp: SimpleHttp,
  val metrics: Metrics,
  val tools: Tools,
  servicesConfig: ServicesConfig)
    extends HasMetrics {

  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = configuration
  lazy val addressLookupUrl = servicesConfig.baseUrl("address-lookup")

  def lookup(postcode: String, filter: Option[String] = None)(
    implicit hc: HeaderCarrier): Future[AddressLookupResponse] =
    withMetricsTimer("address-lookup") { t =>
      val hn = tools.urlEncode(filter.getOrElse(""))
      val pc = postcode.replaceAll(" ", "")
      val newHc = hc.withExtraHeaders("X-Hmrc-Origin" -> configDecorator.origin)

      simpleHttp.get[AddressLookupResponse](s"$addressLookupUrl/v1/gb/addresses.json?postcode=$pc&filter=$hn")(
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            AddressLookupSuccessResponse(RecordSet.fromJsonAddressLookupService(r.json))

          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Unexpected ${r.status} response getting address record from address lookup service")
            AddressLookupUnexpectedResponse(r)
        },
        onError = {
          case e =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Error getting address record from address lookup service", e)
            AddressLookupErrorResponse(e)
        }
      )(newHc)
    }
}
