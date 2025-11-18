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

package repositories

import config.{ConfigDecorator, CryptoProvider, SensitiveT}
import play.api.Logging
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongo.cache.{DataKey, SessionCacheRepository as CacheRepository}
import uk.gov.hmrc.mongo.{CurrentTimestampSupport, MongoComponent}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

@Singleton
class EncryptedSessionCacheRepository @Inject() (
  appConfig: ConfigDecorator,
  mongoComponent: MongoComponent,
  cryptoProvider: CryptoProvider
)(implicit ec: ExecutionContext)
    extends CacheRepository(
      mongoComponent = mongoComponent,
      collectionName = "sessions",
      ttl = appConfig.sessionCacheTtl.minutes,
      timestampSupport = new CurrentTimestampSupport(),
      sessionIdKey = SessionKeys.sessionId
    )
    with Logging {

  implicit lazy val symmetricCryptoFactory: Encrypter with Decrypter =
    cryptoProvider.get()

  override def putSession[T: Writes](
    dataKey: DataKey[T],
    data: T
  )(implicit request: RequestHeader): Future[(String, String)] = {

    val jsonData         = if (appConfig.mongoEncryptionEnabled) {
      val encrypter = JsonEncryption.sensitiveEncrypter[T, SensitiveT[T]]
      encrypter.writes(SensitiveT(data))
    } else {
      Json.toJson(data)
    }
    val encryptedDataKey = DataKey[JsValue](dataKey.unwrap)

    cacheRepo
      .put[JsValue](request)(encryptedDataKey, jsonData)
      .map(res => SessionKeys.sessionId -> res.id)
  }

  override def getFromSession[T: Reads](dataKey: DataKey[T])(implicit request: RequestHeader): Future[Option[T]] = {

    val encryptedDataKey                       = DataKey[JsValue](dataKey.unwrap)
    val encryptedData: Future[Option[JsValue]] = cacheRepo.get[JsValue](request)(encryptedDataKey)

    encryptedData.map { maybeCache =>
      if (appConfig.mongoEncryptionEnabled) {
        val decrypter = JsonEncryption.sensitiveDecrypter[T, SensitiveT[T]](SensitiveT.apply)
        maybeCache.flatMap { cache =>
          Try(cache.as[SensitiveT[T]](decrypter).decryptedValue) match {
            case Success(decrypted) => Some(decrypted)
            case Failure(exception) =>
              logger.error(s"could not decrypt ${dataKey.unwrap}")
              None
          }
        }
      } else {
        maybeCache.map(_.as[T])
      }
    }

  }

  override def deleteFromSession[T](dataKey: DataKey[T])(implicit request: RequestHeader): Future[Unit] = {
    val encryptedDataKey = DataKey[JsValue](dataKey.unwrap)
    cacheRepo.delete(request)(encryptedDataKey)
  }
}
