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
import models.admin.TaxComponentsToggle
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.libs.json.Json
import testUtils.BaseSpec
import testUtils.Fixtures.fakeNino
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.Future

class TaiServiceSpec extends BaseSpec {

  lazy val connector: TaiConnector = mock[TaiConnector]

  val fakeTaxYear: Int = TaxYear.now().getYear

  def sut: TaiService = new TaiService(connector, mockFeatureFlagService)(ec)

  when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsToggle)))
    .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsToggle, isEnabled = true)))

  "TaiService" when {
    "retrieveTaxComponentsState" must {
      "handle invalid tax year" in {
        val invalidTaxYear = -1

        when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsToggle)))
          .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsToggle, isEnabled = true)))

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
    }

    "Toggle isEnabled" must {
      "return success if taxComponents are present" in {
        val taxComponentsList = List("MarriageAllowanceReceived", "CarBenefit")
        val taxComponentsJson = Json.obj("taxComponents" -> taxComponentsList)

        when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsToggle)))
          .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsToggle, isEnabled = true)))

        when(connector.taxComponents(any(), any())(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, List[String]](
              Future.successful(
                Right(taxComponentsList)
              )
            )
          )

        val result = sut.get(fakeNino, fakeTaxYear)

        result.map { state =>
          state.nonEmpty mustBe true
        }
      }

      "return List.empty if bad request" in {
        when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsToggle)))
          .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsToggle, isEnabled = true)))

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
        when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsToggle)))
          .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsToggle, isEnabled = true)))

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
        when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsToggle)))
          .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsToggle, isEnabled = true)))

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

    "Toggle isDisabled" must {
      "return empty list if TaxComponentsDisabledState when TaxComponents are not present" in {
        when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsToggle)))
          .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsToggle, isEnabled = false)))

        val result = sut.get(fakeNino, fakeTaxYear)

        result.map { state =>
          state mustBe List.empty
        }
      }

    }
  }
}
