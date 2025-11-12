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

package services

import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.ArgumentMatchers
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.EncryptedSessionCacheRepository
import testUtils.BaseSpec
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future

class CacheServiceSpec extends BaseSpec {
  private val mockEncryptedSessionCacheRepository = mock[EncryptedSessionCacheRepository]
  private val sut: CacheService                   = new CacheService(mockEncryptedSessionCacheRepository)
  private val key: String                         = "key"
  private val dummyValue: String                  = "dummy"
  private val upstreamErrorResponse               = UpstreamErrorResponse("", 500, 500)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEncryptedSessionCacheRepository)
    ()
  }

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  def block: EitherT[Future, UpstreamErrorResponse, String] =
    EitherT.rightT[Future, UpstreamErrorResponse](dummyValue)

  def blockError: EitherT[Future, UpstreamErrorResponse, String] =
    EitherT.leftT[Future, String](upstreamErrorResponse)

  "cache" must {
    "return cache when value present in cache" in {
      when(mockEncryptedSessionCacheRepository.getFromSession[String](DataKey(any[String]()))(any(), any()))
        .thenReturn(Future.successful(Some(dummyValue)))
      when(mockEncryptedSessionCacheRepository.putSession[String](DataKey(any[String]()), any())(any(), any()))
        .thenReturn(Future.successful(("1", dummyValue)))

      val result = sut.cache(key)(block).value.futureValue
      result mustBe Right(dummyValue)
      verify(mockEncryptedSessionCacheRepository, times(1)).getFromSession[String](DataKey(any[String]()))(any(), any())
      verify(mockEncryptedSessionCacheRepository, times(0))
        .putSession[String](DataKey(any[String]()), ArgumentMatchers.eq[String](dummyValue))(any(), any())
    }

    "return value and cache value when value not present in cache" in {
      when(mockEncryptedSessionCacheRepository.getFromSession[String](DataKey(any[String]()))(any(), any()))
        .thenReturn(Future.successful(None))
      when(mockEncryptedSessionCacheRepository.putSession[String](DataKey(any[String]()), any())(any(), any()))
        .thenReturn(Future.successful(("1", dummyValue)))

      val result = sut.cache(key)(block).value.futureValue
      result mustBe Right(dummyValue)
      verify(mockEncryptedSessionCacheRepository, times(1)).getFromSession[String](DataKey(any[String]()))(any(), any())
      verify(mockEncryptedSessionCacheRepository, times(1))
        .putSession[String](DataKey(any[String]()), ArgumentMatchers.eq[String](dummyValue))(any(), any())
    }

    "return value and not cache value when value not present in cache and an error is returned upstream" in {
      when(mockEncryptedSessionCacheRepository.getFromSession[String](DataKey(any[String]()))(any(), any()))
        .thenReturn(Future.successful(None))
      when(mockEncryptedSessionCacheRepository.putSession[String](DataKey(any[String]()), any())(any(), any()))
        .thenReturn(Future.successful(("1", dummyValue)))

      val result = sut.cache(key)(blockError).value.futureValue
      result mustBe Left(upstreamErrorResponse)
      verify(mockEncryptedSessionCacheRepository, times(1)).getFromSession[String](DataKey(any[String]()))(any(), any())
      verify(mockEncryptedSessionCacheRepository, times(0))
        .putSession[String](DataKey(any[String]()), ArgumentMatchers.eq[String](dummyValue))(any(), any())
    }

  }

}
