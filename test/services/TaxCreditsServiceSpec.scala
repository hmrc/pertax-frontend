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

package services

import cats.data.EitherT
import connectors.TaxCreditsConnector
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status._
import testUtils.BaseSpec
import testUtils.Fixtures.fakeNino
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import scala.concurrent.Future

class TaxCreditsServiceSpec extends BaseSpec {

  lazy val connector = mock[TaxCreditsConnector]

  def sut = new TaxCreditsService(connector)

  "TaxCreditsService" when {
    "I call checkForTaxCredits" must {
      "return Some(true) if TCS data is available for a given NINO" in {
        when(connector.checkForTaxCredits(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](Future(Right(HttpResponse(OK, ""))))
          )

        val result = sut.checkForTaxCredits(Some(fakeNino)).value.futureValue

        result mustBe Some(true)
      }

      "return Some(false) if no NINO is provided" in {
        when(connector.checkForTaxCredits(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](Future(Right(HttpResponse(OK, ""))))
          )

        val result = sut.checkForTaxCredits(None).value.futureValue

        result mustBe Some(false)
      }

      "return Some(false) if TCS data is NOT_FOUND for a given NINO" in {
        when(connector.checkForTaxCredits(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](Future(Left(UpstreamErrorResponse("", NOT_FOUND))))
          )

        val result = sut.checkForTaxCredits(Some(fakeNino)).value.futureValue

        result mustBe Some(false)
      }

      List(
        BAD_REQUEST,
        IM_A_TEAPOT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE
      ).foreach { status =>
        s"return None if TCS data is $status for a given NINO" in {
          when(connector.checkForTaxCredits(any())(any()))
            .thenReturn(
              EitherT[Future, UpstreamErrorResponse, HttpResponse](Future(Left(UpstreamErrorResponse("", status))))
            )

          val result = sut.checkForTaxCredits(Some(fakeNino)).value.futureValue

          result mustBe None
        }
      }
    }
  }
}
