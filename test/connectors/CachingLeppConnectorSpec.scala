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

package connectors

import cats.data.EitherT
import models.LeppSummaryResponse
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import services.CacheService
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class CachingLeppConnectorSpec extends ConnectorSpec with BaseSpec with WireMockHelper {

  private val injectedCacheService: CacheService = app.injector.instanceOf[CacheService]
  private val mockLeppConnector: LeppConnector = mock[LeppConnector]

  override implicit val hc: HeaderCarrier = HeaderCarrier()
  override implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private def connector: CachingLeppConnector =
    new CachingLeppConnector(mockLeppConnector, injectedCacheService)

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockLeppConnector)
  }

  "CachingLeppConnector.getLeppSummary" must {

    "fetch from service cache" in {
      val response = LeppSummaryResponse("PAYMENTS_AVAILABLE")
      when(mockLeppConnector.getLeppSummary(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](response))

      val result = connector.getLeppSummary.value.futureValue

      result mustBe Right(response)
    }
  }
}
