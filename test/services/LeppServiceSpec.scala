/*
 * Copyright 2026 HM Revenue & Customs
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
import config.ConfigDecorator
import connectors.LeppConnector
import models.LeppSummaryResponse
import models.admin.LowEarnersPensionsPaymentToggle
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class LeppServiceSpec extends BaseSpec {

  private val mockLeppConnector: LeppConnector     = mock[LeppConnector]
  private val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]
  private val startUrl                             =
    "https://www.tax.service.gov.uk/accept-your-low-earners-pension-payment/start"
  private val paymentsUrl                          =
    "https://www.tax.service.gov.uk/accept-your-low-earners-pension-payment/payments"
  private val sut: LeppService                     =
    new LeppService(mockLeppConnector, mockFeatureFlagService, mockConfigDecorator)

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockLeppConnector)
    reset(mockConfigDecorator)

    when(mockConfigDecorator.leppStartUrl).thenReturn(startUrl)
    when(mockConfigDecorator.leppPaymentsUrl).thenReturn(paymentsUrl)
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(LowEarnersPensionsPaymentToggle)))
      .thenReturn(Future.successful(FeatureFlag(LowEarnersPensionsPaymentToggle, isEnabled = true)))
  }

  private def stubSummary(status: String): Unit =
    when(mockLeppConnector.getLeppSummary(any(), any(), any()))
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](LeppSummaryResponse(status)))

  "getLeppLink" must {

    "return None and not call LEPP when the toggle is disabled" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(LowEarnersPensionsPaymentToggle)))
        .thenReturn(Future.successful(FeatureFlag(LowEarnersPensionsPaymentToggle, isEnabled = false)))

      sut.getLeppLink.futureValue mustBe None
      verify(mockLeppConnector, times(0)).getLeppSummary(any(), any(), any())
    }

    "return the start URL when payments are available" in {
      stubSummary("PAYMENTS_AVAILABLE")

      sut.getLeppLink.futureValue mustBe Some(startUrl)
    }

    "return the payments URL when no actions are available" in {
      stubSummary("NO_ACTIONS")

      sut.getLeppLink.futureValue mustBe Some(paymentsUrl)
    }

    "return None when the user is not eligible" in {
      stubSummary("NOT_ELIGIBLE")

      sut.getLeppLink.futureValue mustBe None
    }

    "return None for CHECK because the ticket does not define a tile action for it" in {
      stubSummary("CHECK")

      sut.getLeppLink.futureValue mustBe None
    }

    "return None for an unknown status" in {
      stubSummary("UNKNOWN")

      sut.getLeppLink.futureValue mustBe None
    }

    "return None when the LEPP backend returns an error" in {
      when(mockLeppConnector.getLeppSummary(any(), any(), any()))
        .thenReturn(
          EitherT.leftT[Future, LeppSummaryResponse](
            UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR)
          )
        )

      sut.getLeppLink.futureValue mustBe None
    }

    "return None when the LEPP backend does not respond" in {
      when(mockLeppConnector.getLeppSummary(any(), any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, LeppSummaryResponse](
            Future.failed(new RuntimeException("No response"))
          )
        )

      sut.getLeppLink.futureValue mustBe None
    }
  }
}
