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

package services

import cats.data.EitherT
import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.{Request, RequestHeader}
import repositories.EncryptedSessionCacheRepository
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}

class CacheService @Inject() (sessionCacheRepository: EncryptedSessionCacheRepository)(implicit
  ec: ExecutionContext
) extends Logging {

  def deleteFromCache(cacheKey: String)(implicit request: RequestHeader): Future[Unit] =
    sessionCacheRepository.deleteFromSession(DataKey[JsValue](cacheKey))

  def deleteFromCacheAsEitherT[L](cacheKey: String)(implicit request: RequestHeader): EitherT[Future, L, Unit] =
    EitherT[Future, L, Unit](sessionCacheRepository.deleteFromSession(DataKey[JsValue](cacheKey)).map(Right(_)))

  def cache[L, A: Format](
    key: String
  )(f: => EitherT[Future, L, A])(implicit request: Request[_]): EitherT[Future, L, A] = {
    def fetchAndCache: EitherT[Future, L, A] =
      for {
        result <- f
        _      <- EitherT[Future, L, (String, String)](
                    sessionCacheRepository
                      .putSession[A](DataKey[A](key), result)
                      .map(Right(_))
                  )
      } yield result

    def readAndUpdate: EitherT[Future, L, A] =
      EitherT(
        sessionCacheRepository
          .getFromSession[A](DataKey[A](key))
          .flatMap {
            case None        =>
              fetchAndCache.value
            case Some(value) =>
              Future.successful(Right(value))
          }
      )

    readAndUpdate
  }

}
