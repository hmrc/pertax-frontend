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
import models.enrolments.{EACDEnrolment, IdentifiersOrVerifiers, KnownFactsRequest, KnownFactsResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import play.api.libs.json.*
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.EncryptedSessionCacheRepository
import services.CacheService
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}

class CachingEnrolmentsConnectorSpec extends ConnectorSpec with BaseSpec with WireMockHelper {

  val mockUnderlyingConnector: EnrolmentsConnector                         = mock[EnrolmentsConnector]
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

  def connector: CachingEnrolmentsConnector =
    new CachingEnrolmentsConnector(mockUnderlyingConnector, spyCacheService)

  implicit val userRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  "Calling getKnownFacts" when {
    "retrieve cache" in {
      val knownFactsRequest          = KnownFactsRequest("service", List(IdentifiersOrVerifiers("key", "value")))
      val expectedKnownFactsResponse = KnownFactsResponse(
        "service",
        List(
          EACDEnrolment(
            List(IdentifiersOrVerifiers("identifier", "1")),
            List(IdentifiersOrVerifiers("verifiers", "2"))
          )
        )
      )

      when(mockEncryptedSessionCacheRepository.deleteFromSession(DataKey[JsValue](any()))(any())).thenReturn(
        Future.unit
      )
      when(mockEncryptedSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any()))(any(), any()))
        .thenReturn(
          Future.successful(Some(expectedKnownFactsResponse))
        )
      when(mockEncryptedSessionCacheRepository.putSession[JsValue](DataKey[JsValue](any()), any())(any(), any()))
        .thenReturn(
          Future.successful(("1", expectedKnownFactsResponse))
        )

      when(mockUnderlyingConnector.getKnownFacts(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](expectedKnownFactsResponse)
      )

      val result = connector.getKnownFacts(knownFactsRequest).value.futureValue

      result mustBe Right(expectedKnownFactsResponse)

      verify(mockEncryptedSessionCacheRepository, times(0)).deleteFromSession(DataKey[JsValue](any()))(any())
      verify(mockEncryptedSessionCacheRepository, times(1))
        .getFromSession[JsValue](DataKey[JsValue](any()))(any(), any())
      verify(mockEncryptedSessionCacheRepository, times(0))
        .putSession[JsValue](DataKey[JsValue](any()), any())(any(), any())
      verify(mockUnderlyingConnector, times(0)).getKnownFacts(any())(any(), any())
    }

    "fetch and fill cache" in {
      val knownFactsRequest          = KnownFactsRequest("service", List(IdentifiersOrVerifiers("key", "value")))
      val expectedKnownFactsResponse = KnownFactsResponse(
        "service",
        List(
          EACDEnrolment(
            List(IdentifiersOrVerifiers("identifier", "1")),
            List(IdentifiersOrVerifiers("verifiers", "2"))
          )
        )
      )

      when(mockEncryptedSessionCacheRepository.deleteFromSession(DataKey[JsValue](any()))(any())).thenReturn(
        Future.unit
      )
      when(mockEncryptedSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any()))(any(), any()))
        .thenReturn(
          Future.successful(None)
        )
      when(mockEncryptedSessionCacheRepository.putSession[JsValue](DataKey[JsValue](any()), any())(any(), any()))
        .thenReturn(
          Future.successful(("1", expectedKnownFactsResponse))
        )

      when(mockUnderlyingConnector.getKnownFacts(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](expectedKnownFactsResponse)
      )

      val result = connector.getKnownFacts(knownFactsRequest).value.futureValue

      result mustBe Right(expectedKnownFactsResponse)

      verify(mockEncryptedSessionCacheRepository, times(0)).deleteFromSession(DataKey[JsValue](any()))(any())
      verify(mockEncryptedSessionCacheRepository, times(1))
        .getFromSession[JsValue](DataKey[JsValue](any()))(any(), any())
      verify(mockEncryptedSessionCacheRepository, times(1))
        .putSession[JsValue](DataKey[JsValue](any()), any())(any(), any())
      verify(mockUnderlyingConnector, times(1)).getKnownFacts(any())(any(), any())
    }
  }

  "Calling getUserIdsWithEnrolments" must {
    "retrieve cache" in {
      val enrolmentKey = "enrolmentKey"
      val credIds      = Seq("cred1", "cred2")

      when(mockEncryptedSessionCacheRepository.deleteFromSession(DataKey[JsValue](any()))(any())).thenReturn(
        Future.unit
      )
      when(mockEncryptedSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any()))(any(), any()))
        .thenReturn(
          Future.successful(Some(credIds))
        )
      when(mockEncryptedSessionCacheRepository.putSession[JsValue](DataKey[JsValue](any()), any())(any(), any()))
        .thenReturn(
          Future.successful(("1", credIds))
        )

      when(mockUnderlyingConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](credIds)
      )

      val result = connector.getUserIdsWithEnrolments(enrolmentKey).value.futureValue

      result mustBe Right(credIds)

      verify(mockEncryptedSessionCacheRepository, times(0)).deleteFromSession(DataKey[JsValue](any()))(any())
      verify(mockEncryptedSessionCacheRepository, times(1))
        .getFromSession[JsValue](DataKey[JsValue](any()))(any(), any())
      verify(mockEncryptedSessionCacheRepository, times(0))
        .putSession[JsValue](DataKey[JsValue](any()), any())(any(), any())
      verify(mockUnderlyingConnector, times(0)).getUserIdsWithEnrolments(any())(any(), any())
    }

    "fetch and fill cache" in {
      val enrolmentKey = "enrolmentKey"
      val credIds      = Seq("cred1", "cred2")

      when(mockEncryptedSessionCacheRepository.deleteFromSession(DataKey[JsValue](any()))(any())).thenReturn(
        Future.unit
      )
      when(mockEncryptedSessionCacheRepository.getFromSession[JsValue](DataKey[JsValue](any()))(any(), any()))
        .thenReturn(
          Future.successful(None)
        )
      when(mockEncryptedSessionCacheRepository.putSession[JsValue](DataKey[JsValue](any()), any())(any(), any()))
        .thenReturn(
          Future.successful(("1", credIds))
        )

      when(mockUnderlyingConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](credIds)
      )

      val result = connector.getUserIdsWithEnrolments(enrolmentKey).value.futureValue

      result mustBe Right(credIds)

      verify(mockEncryptedSessionCacheRepository, times(0)).deleteFromSession(DataKey[JsValue](any()))(any())
      verify(mockEncryptedSessionCacheRepository, times(1))
        .getFromSession[JsValue](DataKey[JsValue](any()))(any(), any())
      verify(mockEncryptedSessionCacheRepository, times(1))
        .putSession[JsValue](DataKey[JsValue](any()), any())(any(), any())
      verify(mockUnderlyingConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
    }

  }
}
