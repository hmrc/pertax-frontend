/*
 * Copyright 2025 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import services.CacheService
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class CachingTaiConnectorSpec extends ConnectorSpec with BaseSpec with WireMockHelper {

  val injectedCacheService: CacheService = app.injector.instanceOf[CacheService]
  val mockTaiConnector: TaiConnector     = mock[TaiConnector]

  override implicit val hc: HeaderCarrier         = HeaderCarrier()
  override implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def connector: CachingTaiConnector = new CachingTaiConnector(mockTaiConnector, injectedCacheService)

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private val nino = Nino("AA123456A")
  private val year = 2024

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaiConnector)
  }

  "CachingTaiConnector.taxComponents" must {

    "fetch from service cache" in {
      val json = Json.obj("test" -> "testValue")
      when(mockTaiConnector.taxComponents(any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](json)
      )

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe Right(json)
    }
  }
}
