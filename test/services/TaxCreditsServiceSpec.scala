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
import play.api.http.Status._
import testUtils.BaseSpec
import testUtils.Fixtures.fakeNino
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class TaxCreditsServiceSpec extends BaseSpec {

  lazy val connector: TaxCreditsConnector = mock[TaxCreditsConnector]

  def sut: TaxCreditsService = new TaxCreditsService(connector)

  "TaxCreditsService" when {
    "I call isAddressChangeInPTA" must {
      "return None if exclusion is true" in {
        when(connector.getTaxCreditsExclusionStatus(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Boolean](Future(Right(true)))
          )

        val result = sut.isAddressChangeInPTA(fakeNino).value.futureValue

        result mustBe Right(None)
      }

      "return Some(true) if not found in TCS" in {
        when(connector.getTaxCreditsExclusionStatus(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Boolean](Future(Left(UpstreamErrorResponse("", NOT_FOUND))))
          )

        val result = sut.isAddressChangeInPTA(fakeNino).value.futureValue

        result mustBe Right(Some(true))
      }

      "return Some(false) if found in TCS + not exclusion" in {
        when(connector.getTaxCreditsExclusionStatus(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Boolean](Future(Right(false)))
          )

        val result = sut.isAddressChangeInPTA(fakeNino).value.futureValue

        result mustBe Right(Some(false))
      }

      List(
        BAD_REQUEST,
        IM_A_TEAPOT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE
      ).foreach { status =>
        s"return left upstream error response if TCS data is $status for a given NINO" in {
          when(connector.getTaxCreditsExclusionStatus(any())(any()))
            .thenReturn(
              EitherT[Future, UpstreamErrorResponse, Boolean](Future(Left(UpstreamErrorResponse("", status))))
            )

          val result = sut.isAddressChangeInPTA(fakeNino).value.futureValue
          result mustBe Left(UpstreamErrorResponse("", status))
        }
      }
    }
  }
}
