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
import models.SummaryCardPartial
import models.admin.TaxcalcMakePaymentLinkToggle
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.twirl.api.Html
import services.partials.TaxCalcPartialService
import testUtils.BaseSpec
import testUtils.Fixtures._
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class TaxCalcPartialServiceSpec extends BaseSpec {
  private val mockEnhancedPartialRetriever: EnhancedPartialRetriever = mock[EnhancedPartialRetriever]
  private val mockConfigDecorator                                    = mock[ConfigDecorator]
  private val timeoutValue                                           = 100

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEnhancedPartialRetriever)
    reset(mockConfigDecorator)
    when(mockConfigDecorator.taxCalcPartialTimeoutInMilliseconds).thenReturn(timeoutValue)
  }

  trait LocalSetup {
    val taxCalcPartialService: TaxCalcPartialService = new TaxCalcPartialService(
      mockConfigDecorator,
      mockEnhancedPartialRetriever,
      mockFeatureFlagService
    )
  }

  "Calling getTaxCalcPartial" must {

    "return empty list for tax calc" when {
      "TaxcalcMakePaymentLinkToggle is disabled" in new LocalSetup {
        when(mockConfigDecorator.taxCalcFormPartialLinkUrl).thenReturn("test-url")
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcMakePaymentLinkToggle)))
          .thenReturn(Future.successful(FeatureFlag(TaxcalcMakePaymentLinkToggle, isEnabled = false)))
        when(mockEnhancedPartialRetriever.loadPartialSeqSummaryCard(any(), any())(any(), any())) thenReturn
          Future.successful[Seq[SummaryCardPartial]](Seq(SummaryCardPartial("Title", Html("<title/>"))))

        val result: Seq[SummaryCardPartial] =
          taxCalcPartialService.getTaxCalcPartial(buildFakeRequestWithAuth("GET")).futureValue
        result mustBe Nil
        verify(mockEnhancedPartialRetriever, times(0))
          .loadPartialSeqSummaryCard(any(), ArgumentMatchers.eq(timeoutValue))(any(), any())
      }
    }

    "return non-empty list for tax calc" when {
      "TaxcalcMakePaymentLinkToggle is enabled" in new LocalSetup {
        private val summaryCardPartialData = Seq(SummaryCardPartial("Title", Html("<title/>")))
        when(mockConfigDecorator.taxCalcFormPartialLinkUrl).thenReturn("test-url")
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcMakePaymentLinkToggle)))
          .thenReturn(Future.successful(FeatureFlag(TaxcalcMakePaymentLinkToggle, isEnabled = true)))
        when(
          mockEnhancedPartialRetriever.loadPartialSeqSummaryCard(any(), ArgumentMatchers.eq(timeoutValue))(any(), any())
        ) thenReturn
          Future.successful[Seq[SummaryCardPartial]](summaryCardPartialData)

        val result: Seq[SummaryCardPartial] =
          taxCalcPartialService.getTaxCalcPartial(buildFakeRequestWithAuth("GET")).futureValue
        result mustBe summaryCardPartialData
        verify(mockEnhancedPartialRetriever, times(1)).loadPartialSeqSummaryCard(any(), any())(any(), any())
      }
    }
  }
}
