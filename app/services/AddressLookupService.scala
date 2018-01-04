/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import com.kenshoo.play.metrics.Metrics
import controllers.auth.PertaxAuthenticationProvider
import metrics._
import play.api.Logger
import services.http.SimpleHttp
import models.addresslookup.RecordSet
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.Future
import util._
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

sealed trait AddressLookupResponse
case class AddressLookupSuccessResponse(addressList: RecordSet) extends AddressLookupResponse
case class AddressLookupUnexpectedResponse(r: HttpResponse) extends AddressLookupResponse
case class AddressLookupErrorResponse(cause: Exception) extends AddressLookupResponse


@Singleton
class AddressLookupService @Inject() (val simpleHttp: SimpleHttp, val metrics: Metrics, val pertaxAuthenticationProvider: PertaxAuthenticationProvider) extends ServicesConfig with HasMetrics {

  lazy val addressLookupUrl = baseUrl("address-lookup")

  def lookup(postcode: String, filter: Option[String] = None)(implicit hc: HeaderCarrier): Future[AddressLookupResponse] = {
    withMetricsTimer("address-lookup") { t =>

      val hn = Tools.urlEncode(filter.getOrElse(""))
      val pc = postcode.replaceAll(" ", "")
      val newHc = hc.withExtraHeaders("X-Hmrc-Origin" -> pertaxAuthenticationProvider.defaultOrigin)

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
}
