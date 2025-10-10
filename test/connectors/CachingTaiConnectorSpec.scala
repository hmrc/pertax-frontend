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
import play.api.libs.json.Format
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.SessionCacheRepository
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}

class CachingTaiConnectorSpec extends ConnectorSpec with BaseSpec with WireMockHelper {

  private val mockUnderlying: TaiConnector                       = mock[TaiConnector]
  private val mockSessionCacheRepository: SessionCacheRepository = mock[SessionCacheRepository]

  override implicit val hc: HeaderCarrier         = HeaderCarrier()
  override implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override implicit lazy val app: Application =
    app(
      Map.empty,
      bind(classOf[TaiConnector]).qualifiedWith("default").toInstance(mockUnderlying),
      bind[SessionCacheRepository].toInstance(mockSessionCacheRepository)
    )

  def connector: CachingTaiConnector = inject[CachingTaiConnector]

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private val nino = Nino("AA123456A")
  private val year = 2024
  private val fmt  = implicitly[Format[Boolean]]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUnderlying)
    reset(mockSessionCacheRepository)
  }

  "CachingTaiConnector.taxComponents" must {

    "fetch from underlying and cache when nothing in session cache" in {
      when(mockSessionCacheRepository.getFromSession[Boolean](DataKey[Boolean](any[String]()))(any(), any()))
        .thenReturn(Future.successful(None))

      when(mockUnderlying.taxComponents[Boolean](any(), any())(any[Format[Boolean]]())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(true)))

      when(mockSessionCacheRepository.putSession[Boolean](DataKey[Boolean](any[String]()), any())(any(), any()))
        .thenReturn(Future.successful(("", "")))

      val result =
        connector.taxComponents[Boolean](nino, year)(fmt).value.futureValue

      result mustBe Right(Some(true))

      verify(mockUnderlying, times(1)).taxComponents[Boolean](any(), any())(any[Format[Boolean]]())(any(), any(), any())
      verify(mockSessionCacheRepository, times(1))
        .putSession[Boolean](DataKey[Boolean](any[String]()), eqTo(true))(any(), any())
    }

    "return cached value and not call underlying when cache hit" in {
      when(mockSessionCacheRepository.getFromSession[Boolean](DataKey[Boolean](any[String]()))(any(), any()))
        .thenReturn(Future.successful(Some(true)))

      val result =
        connector.taxComponents[Boolean](nino, year)(fmt).value.futureValue

      result mustBe Right(Some(true))

      verify(mockUnderlying, times(0)).taxComponents[Boolean](any(), any())(any[Format[Boolean]]())(any(), any(), any())
      verify(mockSessionCacheRepository, times(0)).putSession[Boolean](any(), any())(any(), any())
    }

    "not cache when underlying returns Right(None)" in {
      when(mockSessionCacheRepository.getFromSession[Boolean](DataKey[Boolean](any[String]()))(any(), any()))
        .thenReturn(Future.successful(None))

      when(mockUnderlying.taxComponents[Boolean](any(), any())(any[Format[Boolean]]())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](None))

      val result =
        connector.taxComponents[Boolean](nino, year)(fmt).value.futureValue

      result mustBe Right(None)

      verify(mockSessionCacheRepository, times(0)).putSession[Boolean](any(), any())(any(), any())
    }

    "propagate error and do not cache when underlying returns Left(error)" in {
      when(mockSessionCacheRepository.getFromSession[Boolean](DataKey[Boolean](any[String]()))(any(), any()))
        .thenReturn(Future.successful(None))

      val err = UpstreamErrorResponse("boom", 500)

      when(mockUnderlying.taxComponents[Boolean](any(), any())(any[Format[Boolean]]())(any(), any(), any()))
        .thenReturn(EitherT.leftT[Future, Option[Boolean]](err))

      val result =
        connector.taxComponents[Boolean](nino, year)(fmt).value.futureValue

      result mustBe Left(err)

      verify(mockSessionCacheRepository, times(0)).putSession[Boolean](any(), any())(any(), any())
    }
  }
}
