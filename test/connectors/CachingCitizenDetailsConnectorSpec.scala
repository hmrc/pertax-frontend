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

package connectors

import cats.data.EitherT
import cats.implicits.*
import models.Address
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, spy, times, verify, when}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import services.CacheService
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import play.api.libs.json.Json
import play.api.libs.json.*
import repositories.EncryptedSessionCacheRepository
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future
import java.time.LocalDate
import scala.concurrent.ExecutionContext

class CachingCitizenDetailsConnectorSpec extends ConnectorSpec with BaseSpec with WireMockHelper {

  val mockUnderlyingConnector: CitizenDetailsConnector                     = mock[CitizenDetailsConnector]
  val mockEncryptedSessionCacheRepository: EncryptedSessionCacheRepository = mock[EncryptedSessionCacheRepository]

  val spyCacheService: CacheService = spy(
    new CacheService(mockEncryptedSessionCacheRepository)
  )

  override implicit val hc: HeaderCarrier         = HeaderCarrier()
  override implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUnderlyingConnector)
    reset(mockEncryptedSessionCacheRepository)
  }

  def connector: CachingCitizenDetailsConnector =
    new CachingCitizenDetailsConnector(mockUnderlyingConnector, spyCacheService)

  def url(nino: String): String = s"/citizen-details/$nino/designatory-details?cached=true"

  implicit val userRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  val address: Address = Address(
    line1 = Some("1 Fake Street"),
    line2 = Some("Fake Town"),
    line3 = Some("Fake City"),
    line4 = Some("Fake Region"),
    line5 = None,
    postcode = None,
    country = Some("AA1 1AA"),
    startDate = Some(LocalDate.of(2015, 3, 15)),
    endDate = None,
    `type` = Some("Residential"),
    isRls = false
  )

  "Calling personDetails" when {
    "refreshCache is true" must {
      "invalidate cache and call connector" in {
        val data = Json.obj("personDetails" -> "some content")

        when(mockEncryptedSessionCacheRepository.deleteFromSession(DataKey[JsValue](any()))(any())).thenReturn(
          Future.unit
        )
        when(mockEncryptedSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any()))(any(), any()))
          .thenReturn(
            Future.successful(None)
          )
        when(mockEncryptedSessionCacheRepository.putSession[JsValue](DataKey[JsValue](any()), any())(any(), any()))
          .thenReturn(
            Future.successful(("1", data))
          )

        when(mockUnderlyingConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](data)
        )

        val result = connector.personDetails(generatedNino, true).value.futureValue

        result mustBe Right(data)

        verify(mockEncryptedSessionCacheRepository, times(1)).deleteFromSession(DataKey[JsValue](any()))(any())
        verify(mockEncryptedSessionCacheRepository, times(1))
          .getFromSession[JsValue](DataKey[JsValue](any()))(any(), any())
        verify(mockEncryptedSessionCacheRepository, times(1))
          .putSession[JsValue](DataKey[JsValue](any()), any())(any(), any())
        verify(mockUnderlyingConnector, times(1)).personDetails(any(), any())(any(), any(), any())
      }
    }

    "refreshCache is false" must {
      "retrieve cache" in {
        val data = Json.obj("personDetails" -> "some content")

        when(mockEncryptedSessionCacheRepository.deleteFromSession(DataKey[JsValue](any()))(any())).thenReturn(
          Future.unit
        )
        when(mockEncryptedSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any()))(any(), any()))
          .thenReturn(
            Future.successful(Some(data))
          )
        when(mockEncryptedSessionCacheRepository.putSession[JsValue](DataKey[JsValue](any()), any())(any(), any()))
          .thenReturn(
            Future.successful(("1", data))
          )

        when(mockUnderlyingConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](data)
        )

        val result = connector.personDetails(generatedNino, false).value.futureValue

        result mustBe Right(data)

        verify(mockEncryptedSessionCacheRepository, times(0)).deleteFromSession(DataKey[JsValue](any()))(any())
        verify(mockEncryptedSessionCacheRepository, times(1))
          .getFromSession[JsValue](DataKey[JsValue](any()))(any(), any())
        verify(mockEncryptedSessionCacheRepository, times(0))
          .putSession[JsValue](DataKey[JsValue](any()), any())(any(), any())
        verify(mockUnderlyingConnector, times(0)).personDetails(any(), any())(any(), any(), any())
      }

      "fetch and fill cache" in {
        val data = Json.obj("personDetails" -> "some content")

        when(mockEncryptedSessionCacheRepository.deleteFromSession(DataKey[JsValue](any()))(any())).thenReturn(
          Future.unit
        )
        when(mockEncryptedSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any()))(any(), any()))
          .thenReturn(
            Future.successful(None)
          )
        when(mockEncryptedSessionCacheRepository.putSession[JsValue](DataKey[JsValue](any()), any())(any(), any()))
          .thenReturn(
            Future.successful(("1", data))
          )

        when(mockUnderlyingConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](data)
        )

        val result = connector.personDetails(generatedNino, false).value.futureValue

        result mustBe Right(data)

        verify(mockEncryptedSessionCacheRepository, times(0)).deleteFromSession(DataKey[JsValue](any()))(any())
        verify(mockEncryptedSessionCacheRepository, times(1))
          .getFromSession[JsValue](DataKey[JsValue](any()))(any(), any())
        verify(mockEncryptedSessionCacheRepository, times(1))
          .putSession[JsValue](DataKey[JsValue](any()), any())(any(), any())
        verify(mockUnderlyingConnector, times(1)).personDetails(any(), any())(any(), any(), any())
      }
    }
  }

  "Calling updateAddress" must {
    "clear the cache" when {
      "updating an address" in {
        when(mockUnderlyingConnector.updateAddress(any(), any(), any())(any(), any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](true)
        )
        when(mockEncryptedSessionCacheRepository.deleteFromSession(DataKey[JsValue](any()))(any())).thenReturn(
          Future.unit
        )

        val result = connector.updateAddress(generatedNino, "0", address).value.futureValue
        result mustBe Right(true)

        verify(mockEncryptedSessionCacheRepository, times(1)).deleteFromSession(DataKey[JsValue](any()))(any())
        verify(mockUnderlyingConnector, times(1)).updateAddress(any(), any(), any())(any(), any(), any())

      }

    }
  }
}
