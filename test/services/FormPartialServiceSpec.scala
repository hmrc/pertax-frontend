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

import config.ConfigDecorator
import connectors.EnhancedPartialRetriever
import models.admin.DfsFormsFrontendAvailabilityToggle
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.twirl.api.Html
import services.partials.FormPartialService
import testUtils.BaseSpec
import testUtils.Fixtures.*
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.play.partials.HtmlPartial

import scala.concurrent.Future

class FormPartialServiceSpec extends BaseSpec {
  private val mockEnhancedPartialRetriever: EnhancedPartialRetriever = mock[EnhancedPartialRetriever]
  private val mockConfigDecorator                                    = mock[ConfigDecorator]
  private val timeoutValue                                           = 100

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEnhancedPartialRetriever)
    reset(mockConfigDecorator)
    when(mockConfigDecorator.dfsPartialTimeoutInMilliseconds).thenReturn(timeoutValue)
  }

  trait LocalSetup {
    val formPartialService: FormPartialService = new FormPartialService(
      mockConfigDecorator,
      mockEnhancedPartialRetriever,
      mockFeatureFlagService
    )
  }

  "Calling FormPartialServiceSpec" must {

    "form list for National insurance return empty" when {
      "DfsDigitalFormFrontendShuttered is disabled" in new LocalSetup {
        when(mockConfigDecorator.nationalInsuranceFormPartialLinkUrl).thenReturn("test-url")
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsFormsFrontendAvailabilityToggle)))
          .thenReturn(Future.successful(FeatureFlag(DfsFormsFrontendAvailabilityToggle, isEnabled = false)))
        when(mockEnhancedPartialRetriever.loadPartial(any(), any())(any(), any())) thenReturn
          Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

        val result: HtmlPartial =
          formPartialService.getNationalInsurancePartial(buildFakeRequestWithAuth("GET")).futureValue
        result mustBe HtmlPartial.Failure(None, "dfs-digital-form-frontend is shuttered")
        verify(mockEnhancedPartialRetriever, times(0))
          .loadPartial(any(), ArgumentMatchers.eq(timeoutValue))(any(), any())
      }
    }

    "form list for Self-assessment return empty" when {
      "DfsDigitalFormFrontendShuttered is disabled" in new LocalSetup {
        when(mockConfigDecorator.selfAssessmentFormPartialLinkUrl).thenReturn("test-url")
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsFormsFrontendAvailabilityToggle)))
          .thenReturn(Future.successful(FeatureFlag(DfsFormsFrontendAvailabilityToggle, isEnabled = false)))
        when(mockEnhancedPartialRetriever.loadPartial(any(), any())(any(), any())) thenReturn
          Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

        val result: HtmlPartial =
          formPartialService.getSelfAssessmentPartial(buildFakeRequestWithAuth("GET")).futureValue
        result mustBe HtmlPartial.Failure(None, "dfs-digital-form-frontend is shuttered")
        verify(mockEnhancedPartialRetriever, times(0))
          .loadPartial(any(), ArgumentMatchers.eq(timeoutValue))(any(), any())
      }
    }

    "return form list for National insurance" in new LocalSetup {
      when(mockConfigDecorator.nationalInsuranceFormPartialLinkUrl).thenReturn("test-url")
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsFormsFrontendAvailabilityToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsFormsFrontendAvailabilityToggle, isEnabled = true)))
      when(mockEnhancedPartialRetriever.loadPartial(any(), ArgumentMatchers.eq(timeoutValue))(any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      val result: HtmlPartial =
        formPartialService.getNationalInsurancePartial(buildFakeRequestWithAuth("GET")).futureValue
      result mustBe HtmlPartial.Success(Some("Title"), Html("<title/>"))
      verify(mockEnhancedPartialRetriever, times(1)).loadPartial(any(), any())(any(), any())
    }

    "return form list for Self-assessment" in new LocalSetup {
      when(mockConfigDecorator.selfAssessmentFormPartialLinkUrl).thenReturn("test-url")
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsFormsFrontendAvailabilityToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsFormsFrontendAvailabilityToggle, isEnabled = true)))
      when(mockEnhancedPartialRetriever.loadPartial(any(), ArgumentMatchers.eq(timeoutValue))(any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      val result: HtmlPartial = formPartialService.getSelfAssessmentPartial(buildFakeRequestWithAuth("GET")).futureValue
      result mustBe HtmlPartial.Success(Some("Title"), Html("<title/>"))
      verify(mockEnhancedPartialRetriever, times(1)).loadPartial(any(), any())(any(), any())
    }
  }

}
