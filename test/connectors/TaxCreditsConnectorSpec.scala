/*
 * Copyright 2021 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import play.api.http.Status.{BAD_REQUEST, OK}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import util.Fixtures.fakeNino
import util.{BaseSpec, NullMetrics, WireMockHelper}

import scala.concurrent.Future

class TaxCreditsConnectorSpec extends BaseSpec with WireMockHelper {

  val http = mock[DefaultHttpClient]
  def connector = new TaxCreditsConnector(http, config, new NullMetrics)
  val url: String = config.tcsBrokerHost + s"/tcs/$fakeNino/dashboard-data"

  "TaxCreditsConnector" when {
    "checkForTaxCredits is called" must {
      "return a HttpResponse containing OK if tcs data for the given NINO is found" in {




//
//        when(http.GET[HttpResponse](eqTo(url), any(), any())(any(), any(), any()))
//          .thenReturn(Future.successful(HttpResponse(OK, None)))
//
//        val result = connector.checkForTaxCredits(fakeNino).
//
//        result mustBe HttpResponse(OK, "")

      }
    }
  }

}
