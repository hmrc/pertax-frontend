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
import models.admin.TaxComponentsRetrievalToggle
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.Future

class TaiServiceSpec extends BaseSpec {

  private val mockTaiConnector: TaiConnector             = mock[TaiConnector]
  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaiConnector)
    reset(mockFeatureFlagService)
    ()
  }

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  val sut = new TaiService(mockTaiConnector, mockFeatureFlagService)

  "getTaxComponentsList" when {
    "Tax Component toggle is enabled" must {
      "get a list of tax components" in {
        when(mockTaiConnector.taxComponents(any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](
            Json.parse("""{
                |   "data" : [ {
                |      "componentType" : "EmployerProvidedServices",
                |      "employmentId" : 12,
                |      "amount" : 12321,
                |      "description" : "Some Description",
                |      "iabdCategory" : "Benefit"
                |   }, {
                |      "componentType" : "PersonalPensionPayments",
                |      "employmentId" : 31,
                |      "amount" : 12345,
                |      "description" : "Some Description Some",
                |      "iabdCategory" : "Allowance"
                |   } ],
                |   "links" : [ ]
                |}""".stripMargin)
          )
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
          .thenReturn(
            Future.successful(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true))
          )

        val result = sut.getTaxComponentsList(generatedNino, 2025).futureValue

        result mustBe List("EmployerProvidedServices", "PersonalPensionPayments")
      }

      "get an empty list if there is an error" in {
        when(mockTaiConnector.taxComponents(any(), any())(any(), any(), any())).thenReturn(
          EitherT.leftT[Future, List[String]](UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR))
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
          .thenReturn(
            Future.successful(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true))
          )

        val result = sut.getTaxComponentsList(generatedNino, 2025).futureValue

        result mustBe List.empty
      }
    }

    "Tax Component toggle is disabled" must {
      "get an empty list" in {
        when(mockTaiConnector.taxComponents(any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](
            Json.parse("""{
                |   "data" : [ {
                |      "componentType" : "EmployerProvidedServices",
                |      "employmentId" : 12,
                |      "amount" : 12321,
                |      "description" : "Some Description",
                |      "iabdCategory" : "Benefit"
                |   }, {
                |      "componentType" : "PersonalPensionPayments",
                |      "employmentId" : 31,
                |      "amount" : 12345,
                |      "description" : "Some Description Some",
                |      "iabdCategory" : "Allowance"
                |   } ],
                |   "links" : [ ]
                |}""".stripMargin)
          )
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
          .thenReturn(
            Future.successful(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = false))
          )

        val result = sut.getTaxComponentsList(generatedNino, 2025).futureValue

        result mustBe List.empty

        verify(mockTaiConnector, times(0)).taxComponents(any(), any())(any(), any(), any())
      }
    }

  }

  "isRecipientOfHicBc" when {
    "Tax Component toggle is enabled" must {
      "returns true" in {
        when(mockTaiConnector.taxComponents(any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](
            Json.parse("""{
                |   "data" : [ {
                |      "componentType" : "EmployerProvidedServices",
                |      "employmentId" : 12,
                |      "amount" : 12321,
                |      "description" : "Some Description",
                |      "iabdCategory" : "Benefit"
                |   }, {
                |      "componentType" : "HICBCPaye",
                |      "employmentId" : 31,
                |      "amount" : 12345,
                |      "description" : "Some Description Some",
                |      "iabdCategory" : "Allowance"
                |   } ],
                |   "links" : [ ]
                |}""".stripMargin)
          )
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
          .thenReturn(
            Future.successful(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true))
          )

        val result = sut.isRecipientOfHicBc(generatedNino).futureValue

        result mustBe true
      }

      "returns false when amount is zero" in {
        when(mockTaiConnector.taxComponents(any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](
            Json.parse("""{
                |   "data" : [ {
                |      "componentType" : "EmployerProvidedServices",
                |      "employmentId" : 12,
                |      "amount" : 12321,
                |      "description" : "Some Description",
                |      "iabdCategory" : "Benefit"
                |   }, {
                |      "componentType" : "HICBCPaye",
                |      "employmentId" : 31,
                |      "amount" : 0,
                |      "description" : "Some Description Some",
                |      "iabdCategory" : "Allowance"
                |   } ],
                |   "links" : [ ]
                |}""".stripMargin)
          )
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
          .thenReturn(
            Future.successful(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true))
          )

        val result = sut.isRecipientOfHicBc(generatedNino).futureValue

        result mustBe false
      }

      "returns false when no hicbc is found" in {
        when(mockTaiConnector.taxComponents(any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](
            Json.parse("""{
                |   "data" : [ {
                |      "componentType" : "EmployerProvidedServices",
                |      "employmentId" : 12,
                |      "amount" : 12321,
                |      "description" : "Some Description",
                |      "iabdCategory" : "Benefit"
                |   }],
                |   "links" : [ ]
                |}""".stripMargin)
          )
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
          .thenReturn(
            Future.successful(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true))
          )

        val result = sut.isRecipientOfHicBc(generatedNino).futureValue

        result mustBe false
      }

      "returns false when server error is received" in {
        when(mockTaiConnector.taxComponents(any(), any())(any(), any(), any())).thenReturn(
          EitherT.leftT[Future, Boolean](UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR))
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
          .thenReturn(
            Future.successful(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true))
          )

        val result = sut.isRecipientOfHicBc(generatedNino).futureValue

        result mustBe false
      }

    }

    "Tax Component toggle is disabled" must {
      "return false" in {
        when(mockTaiConnector.taxComponents(any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](
            Json.parse("""{
                |   "data" : [ {
                |      "componentType" : "EmployerProvidedServices",
                |      "employmentId" : 12,
                |      "amount" : 12321,
                |      "description" : "Some Description",
                |      "iabdCategory" : "Benefit"
                |   }, {
                |      "componentType" : "HICBCPaye",
                |      "employmentId" : 31,
                |      "amount" : 12345,
                |      "description" : "Some Description Some",
                |      "iabdCategory" : "Allowance"
                |   } ],
                |   "links" : [ ]
                |}""".stripMargin)
          )
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
          .thenReturn(
            Future.successful(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = false))
          )

        val result = sut.isRecipientOfHicBc(generatedNino).futureValue

        result mustBe false

        verify(mockTaiConnector, times(0)).taxComponents(any(), any())(any(), any(), any())
      }
    }

  }

}
