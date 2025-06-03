/*
 * Copyright 2024 HM Revenue & Customs
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
import connectors.TaiConnector
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND}
import testUtils.BaseSpec
import testUtils.Fixtures.fakeNino
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class TaiServiceSpec extends BaseSpec {

  lazy val connector: TaiConnector = mock[TaiConnector]

  val fakeTaxYear: Int = TaxYear.now().getYear

  def sut: TaiService = new TaiService(connector)(ec)

  "taxComponents" must {
    "handle invalid tax year" in {
      val invalidTaxYear = -1

      when(connector.taxComponents(any(), any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, List[String]](
            Future.successful(
              Left(UpstreamErrorResponse("TaxComponentsNotAvailableState", BAD_REQUEST))
            )
          )
        )

      val result = sut.get(fakeNino, invalidTaxYear)

      result.map { state =>
        state mustBe List.empty
      }
    }
    "return success if taxComponents are present" in {
      val taxComponentsList = List("MarriageAllowanceReceived", "CarBenefit")

      when(connector.taxComponents(any(), any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, List[String]](
            Future.successful(
              Right(taxComponentsList)
            )
          )
        )

      val result = Await.result(sut.get(fakeNino, fakeTaxYear).value, Duration.Inf)
      result mustBe Right(taxComponentsList)
    }

    "return List.empty if bad request" in {
      when(connector.taxComponents(any(), any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, List[String]](
            Future.successful(
              Left(UpstreamErrorResponse("TaxComponentsNotAvailableState", BAD_REQUEST))
            )
          )
        )

      val result = sut.get(fakeNino, fakeTaxYear)

      result.map { state =>
        state mustBe List.empty
      }
    }

    "return List.empty if not found" in {
      when(connector.taxComponents(any(), any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, List[String]](
            Future.successful(
              Left(UpstreamErrorResponse("TaxComponentsNotAvailableState", NOT_FOUND))
            )
          )
        )

      val result = sut.get(fakeNino, fakeTaxYear)

      result.map { state =>
        state mustBe List.empty
      }
    }

    "return List.empty if does not return either handled error" in {
      when(connector.taxComponents(any(), any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, List[String]](
            Future.successful(
              Left(UpstreamErrorResponse("TaxComponentsUnreachableState", INTERNAL_SERVER_ERROR))
            )
          )
        )

      val result = sut.get(fakeNino, fakeTaxYear)

      result.map { state =>
        state mustBe List.empty
      }
    }

  }

}
