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

import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import models.UserAnswers
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model._
import play.api.libs.json.Format
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.cache.CacheIdType.SessionCacheId.NoSessionException
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JourneyCacheRepository @Inject() (
  mongoComponent: MongoComponent,
  appConfig: ConfigDecorator,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[UserAnswers](
      collectionName = "user-answers",
      mongoComponent = mongoComponent,
      domainFormat = UserAnswers.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("sessionId", "nino"),
          IndexOptions()
            .unique(true)
            .name("sessionIdAndNino")
        ),
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions()
            .name("lastUpdatedIdx")
            .expireAfter(
              appConfig.sessionTimeoutInSeconds,
              TimeUnit.SECONDS
            )
        )
      )
    ) {

  implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private def byIdAndNino(sessionId: String, nino: String): Bson =
    Filters.and(
      Filters.equal("sessionId", sessionId),
      Filters.equal("nino", nino)
    )

  def keepAlive(sessionId: String, nino: String): Future[Unit] =
    collection
      .updateOne(
        filter = byIdAndNino(sessionId, nino),
        update = Updates.set("lastUpdated", Instant.now(clock))
      )
      .toFuture()
      .map(_ => (): Unit)

  def get(nino: String)(implicit hc: HeaderCarrier): Future[UserAnswers] = {
    val sessionId            = hc.sessionId.fold(throw NoSessionException)(_.value)
    val retrievedUserAnswers = keepAlive(sessionId, nino).flatMap { _ =>
      collection
        .find(byIdAndNino(sessionId, nino))
        .headOption()
    }
    retrievedUserAnswers.map {
      case None     => UserAnswers(sessionId, nino)
      case Some(ua) => ua
    }
  }

  def set(answers: UserAnswers): Future[Unit] = {
    val updatedAnswers = answers copy (lastUpdated = Instant.now(clock))

    collection
      .replaceOne(
        filter = byIdAndNino(updatedAnswers.sessionId, updatedAnswers.nino),
        replacement = updatedAnswers,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => (): Unit)
  }

  def clear(nino: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    val sessionId = hc.sessionId.fold(throw NoSessionException)(_.value)
    collection
      .deleteOne(byIdAndNino(sessionId, nino))
      .toFuture()
      .map(_ => (): Unit)
  }
}
