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
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.EncryptedSessionCacheRepository
import services.CacheService
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}

class CachingTaiConnectorSpec extends ConnectorSpec with BaseSpec with WireMockHelper {

  private val mockUnderlying: TaiConnector = mock[TaiConnector]
  val mockCacheService: CacheService       = mock[CacheService]

  override implicit val hc: HeaderCarrier         = HeaderCarrier()
  override implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override implicit lazy val app: Application =
    app(
      Map.empty,
      bind(classOf[TaiConnector]).qualifiedWith("default").toInstance(mockUnderlying),
      bind[CacheService].toInstance(mockCacheService)
    )

  def connector: CachingTaiConnector = inject[CachingTaiConnector]

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private val nino = Nino("AA123456A")
  private val year = 2024

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUnderlying)
    reset(mockCacheService)
  }

  "CachingTaiConnector.taxComponents" must {

    "fetch from underlying and cache when nothing in session cache" in {
      val json = Json.obj("test" -> "testValue")
      when(mockCacheService.cache[String, Int](any[String])(any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](json)
      )

      when(mockUnderlying.taxComponents(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](json))

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe Right(json)

      verify(mockUnderlying, times(1)).taxComponents(any(), any())(any(), any(), any())
      verify(mockSessionCacheRepository, times(1))
        .putSession[JsValue](DataKey[JsValue](any[String]()), eqTo(json))(any(), any())
    }

    "return cached value and not call underlying when cache hit" in {
      val json = Json.obj("test" -> "testValue")
      when(mockSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any[String]()))(any(), any()))
        .thenReturn(Future.successful(json))

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe Right(json)

      verify(mockUnderlying, times(0)).taxComponents(any(), any())(any(), any(), any())
      verify(mockSessionCacheRepository, times(0)).putSession[JsValue](any(), any())(any(), any())
    }

    "not cache when underlying returns Right(None)" in {
      when(mockSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any[String]()))(any(), any()))
        .thenReturn(Future.successful(None))

      when(mockUnderlying.taxComponents(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](None))

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe Right(None)

      verify(mockSessionCacheRepository, times(0)).putSession[JsValue](any(), any())(any(), any())
    }

    "propagate error and do not cache when underlying returns Left(error)" in {
      when(mockSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any[String]()))(any(), any()))
        .thenReturn(Future.successful(None))

      val err = UpstreamErrorResponse("boom", 500)

      when(mockUnderlying.taxComponents(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.leftT[Future, Option[Boolean]](err))

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe Left(err)

      verify(mockSessionCacheRepository, times(0)).putSession[JsValue](any(), any())(any(), any())
    }
  }
}
