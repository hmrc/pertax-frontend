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
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import models.addresslookup.{AddressLookup, RecordSet}
import play.api.Logging
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue

@Singleton
class AddressLookupConnector @Inject() (
  configDecorator: ConfigDecorator,
  val httpClientV2: HttpClientV2,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
) extends Logging {

  private lazy val addressLookupUrl: String = servicesConfig.baseUrl("address-lookup")

  def lookup(postcode: String, filter: Option[String] = None)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, RecordSet] = {
    val pc                 = postcode.replaceAll(" ", "")
    val newHc              = hc.withExtraHeaders("X-Hmrc-Origin" -> configDecorator.origin)
    val addressRequestBody = AddressLookup(pc, filter)
    val url                = s"$addressLookupUrl/lookup"

    httpClientResponse
      .read(
        httpClientV2
          .post(url"$url")(newHc)
          .withBody(addressRequestBody)
          .transform(_.withRequestTimeout(configDecorator.addressLookupTimeoutInSec.seconds))
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map(response => RecordSet.fromJsonAddressLookupService(response.json))
  }
}
