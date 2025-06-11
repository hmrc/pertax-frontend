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
import models.{BalancedSA, Overpaid, SummaryCardPartial}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.twirl.api.Html
import services.partials.TaxCalcPartialService
import testUtils.BaseSpec
import testUtils.Fixtures._

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
      mockEnhancedPartialRetriever
    )
  }

  "Calling getTaxCalcPartial" must {
    "return non-empty list for tax calc, excluding balanced SA" in new LocalSetup {
      private val summaryCardPartialData = Seq(
        SummaryCardPartial(
          partialName = "Title",
          partialContent = Html("<title/>"),
          partialReconciliationStatus = Overpaid
        ),
        SummaryCardPartial(
          partialName = "Title",
          partialContent = Html("<title/>"),
          partialReconciliationStatus = BalancedSA
        )
      )
      when(mockConfigDecorator.taxCalcPartialLinkUrl).thenReturn("test-url")
      when(
        mockEnhancedPartialRetriever.loadPartialAsSeqSummaryCard[SummaryCardPartial](
          any(),
          ArgumentMatchers.eq(timeoutValue)
        )(any(), any(), any())
      ) thenReturn
        Future.successful[Seq[SummaryCardPartial]](summaryCardPartialData)

      val result: Seq[SummaryCardPartial] =
        taxCalcPartialService.getTaxCalcPartial(buildFakeRequestWithAuth("GET")).futureValue
      result mustBe summaryCardPartialData.headOption.toSeq
      verify(mockEnhancedPartialRetriever, times(1))
        .loadPartialAsSeqSummaryCard[SummaryCardPartial](any(), any())(any(), any(), any())
    }
  }
}
