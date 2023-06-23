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
import models.admin.{FeatureFlag, NpsOutageToggle}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.twirl.api.Html
import services.admin.FeatureFlagService
import services.partials.FormPartialService
import testUtils.BaseSpec
import testUtils.Fixtures._
import uk.gov.hmrc.play.partials.HtmlPartial

import scala.concurrent.Future

class FormPartialServiceSpec extends BaseSpec {
  val mockEnhancedPartialRetriever: EnhancedPartialRetriever = mock[EnhancedPartialRetriever]
  val mockFeatureFlagService: FeatureFlagService             = mock[FeatureFlagService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEnhancedPartialRetriever, mockFeatureFlagService)
  }

  trait LocalSetup {
    val formPartialService: FormPartialService = new FormPartialService(
      mock[ConfigDecorator],
      mockEnhancedPartialRetriever,
      mockFeatureFlagService
    )
  }

  "Calling FormPartialServiceSpec" must {

    "form list for National insurance return empty" when {
      "NpsOutgaeToggle is enabled" in new LocalSetup {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsOutageToggle)))
          .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, true)))
        when(mockEnhancedPartialRetriever.loadPartial(any())(any(), any())) thenReturn
          Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

        formPartialService.getNationalInsurancePartial(buildFakeRequestWithAuth("GET")).map(p => p mustBe "")
        verify(mockEnhancedPartialRetriever, times(0)).loadPartial(any())(any(), any())
      }
    }

    "form list for Self-assessment return empty" when {
      "NpsOutgaeToggle is enabled" in new LocalSetup {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsOutageToggle)))
          .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, true)))
        when(mockEnhancedPartialRetriever.loadPartial(any())(any(), any())) thenReturn
          Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

        formPartialService.getSelfAssessmentPartial(buildFakeRequestWithAuth("GET")).map(p => p mustBe "")
        verify(mockEnhancedPartialRetriever, times(0)).loadPartial(any())(any(), any())
      }
    }

    "return form list for National insurance" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsOutageToggle)))
        .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, false)))
      when(mockEnhancedPartialRetriever.loadPartial(any())(any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      formPartialService.getNationalInsurancePartial(buildFakeRequestWithAuth("GET")).map(p => p mustBe "<title/>")
      verify(mockEnhancedPartialRetriever, times(1)).loadPartial(any())(any(), any())
    }

    "return form list for Self-assessment" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsOutageToggle)))
        .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, false)))
      when(mockEnhancedPartialRetriever.loadPartial(any())(any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      formPartialService.getSelfAssessmentPartial(buildFakeRequestWithAuth("GET")).map(p => p mustBe "<title/>")
      verify(mockEnhancedPartialRetriever, times(1)).loadPartial(any())(any(), any())
    }
  }

}
