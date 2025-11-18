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

package repositories

import config.{ConfigDecorator, CryptoProvider, SensitiveT}
import org.mockito.Mockito.when
import play.api.Configuration
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongo.cache.DataKey

class SessionCacheRepositorySpec extends BaseSpec {

  val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]
  val injectedConfiguration: Configuration = app.injector.instanceOf[Configuration]
  val cryptoProvider: CryptoProvider       = app.injector.instanceOf[CryptoProvider]

  val repository: EncryptedSessionCacheRepository =
    new EncryptedSessionCacheRepository(mockConfigDecorator, mongoComponent, cryptoProvider)

  val data: JsValue = Json.obj(
    "item 1" -> "Something",
    "item 2" -> true,
    "item 3" -> 11
  )

  val fakeRequest: FakeRequest[AnyContentAsEmpty.type]               = FakeRequest()
    .withSession(
      SessionKeys.authToken -> "Bearer 1",
      SessionKeys.sessionId -> "SessionId"
    )
  implicit lazy val symmetricCryptoFactory: Encrypter with Decrypter =
    cryptoProvider.get()

  val encrypter: Writes[SensitiveT[JsValue]] = JsonEncryption.sensitiveEncrypter[JsValue, SensitiveT[JsValue]]

  "getFromSession retrieve data" when {
    "encryption is enabled and data are encrypted" in {
      when(mockConfigDecorator.mongoEncryptionEnabled).thenReturn(true)

      val encryptedData = encrypter.writes(SensitiveT(data))
      val result        = (for {
        _      <- repository.cacheRepo
                    .put[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino"),
                      encryptedData
                    )
        result <- repository
                    .getFromSession(DataKey[JsValue](s"getPersonDetails-$generatedNino"))(implicitly, fakeRequest)

      } yield result).futureValue

      result mustBe Some(data)

    }

    "encryption is disabled and data are not encrypted" in {
      when(mockConfigDecorator.mongoEncryptionEnabled).thenReturn(false)

      val result = (for {
        _      <- repository.cacheRepo
                    .put[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino"),
                      data
                    )
        result <- repository
                    .getFromSession(DataKey[JsValue](s"getPersonDetails-$generatedNino"))(implicitly, fakeRequest)

      } yield result).futureValue

      result mustBe Some(data)

    }
  }

  "getFromSession returns None" when {
    "encryption is enabled and data cannot be decrypted" in {
      when(mockConfigDecorator.mongoEncryptionEnabled).thenReturn(true)

      val result = (for {
        _      <- repository.cacheRepo
                    .put[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino"),
                      data
                    )
        result <- repository
                    .getFromSession(DataKey[JsValue](s"getPersonDetails-$generatedNino"))(implicitly, fakeRequest)

      } yield result).futureValue

      result mustBe None

    }

  }

  "putSession insert data" when {
    "encryption is enabled" in {
      when(mockConfigDecorator.mongoEncryptionEnabled).thenReturn(true)

      val result = (for {
        _      <- repository
                    .putSession(DataKey[JsValue](s"getPersonDetails-$generatedNino"), data)(implicitly, fakeRequest)
        result <- repository.cacheRepo
                    .get[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino")
                    )
      } yield result).futureValue

      result mustBe Some(encrypter.writes(SensitiveT(data)))
    }

    "encryption is disabled" in {
      when(mockConfigDecorator.mongoEncryptionEnabled).thenReturn(false)

      val result = (for {
        _      <- repository
                    .putSession(DataKey[JsValue](s"getPersonDetails-$generatedNino"), data)(implicitly, fakeRequest)
        result <- repository.cacheRepo
                    .get[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino")
                    )
      } yield result).futureValue

      result mustBe Some(data)
    }
  }

  "deleteFromSession delete data" when {
    "encryption is enabled and data are encrypted" in {
      when(mockConfigDecorator.mongoEncryptionEnabled).thenReturn(true)
      val encryptedData = encrypter.writes(SensitiveT(data))

      val result = (for {
        _      <- repository.cacheRepo
                    .put[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino"),
                      encryptedData
                    )
        _      <- repository.deleteFromSession(DataKey[JsValue](s"getPersonDetails-$generatedNino"))(fakeRequest)
        result <- repository.cacheRepo
                    .get[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino")
                    )
      } yield result).futureValue

      result mustBe None
    }

    "encryption is enabled and data are decrypted" in {
      when(mockConfigDecorator.mongoEncryptionEnabled).thenReturn(true)
      val result = (for {
        _      <- repository.cacheRepo
                    .put[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino"),
                      data
                    )
        _      <- repository.deleteFromSession(DataKey[JsValue](s"getPersonDetails-$generatedNino"))(fakeRequest)
        result <- repository.cacheRepo
                    .get[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino")
                    )
      } yield result).futureValue

      result mustBe None
    }

    "encryption is disabled and data are encrypted" in {
      when(mockConfigDecorator.mongoEncryptionEnabled).thenReturn(false)
      val encryptedData = encrypter.writes(SensitiveT(data))

      val result = (for {
        _      <- repository.cacheRepo
                    .put[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino"),
                      encryptedData
                    )
        _      <- repository.deleteFromSession(DataKey[JsValue](s"getPersonDetails-$generatedNino"))(fakeRequest)
        result <- repository.cacheRepo
                    .get[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino")
                    )
      } yield result).futureValue

      result mustBe None
    }

    "encryption is disabled and data are decrypted" in {
      when(mockConfigDecorator.mongoEncryptionEnabled).thenReturn(false)
      val result = (for {
        _      <- repository.cacheRepo
                    .put[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino"),
                      data
                    )
        _      <- repository.deleteFromSession(DataKey[JsValue](s"getPersonDetails-$generatedNino"))(fakeRequest)
        result <- repository.cacheRepo
                    .get[JsValue](fakeRequest)(
                      DataKey[JsValue](s"getPersonDetails-$generatedNino")
                    )
      } yield result).futureValue

      result mustBe None
    }

  }
}
