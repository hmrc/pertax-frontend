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
          Indexes.ascending("_id"),
          IndexOptions()
            .name("_id")
        ),
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions()
            .name("lastUpdatedIdx")
            .expireAfter(
              appConfig.sessionTimeoutInSeconds.toLong,
              TimeUnit.SECONDS
            )
        )
      )
    ) {

  implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private def byId(id: String): Bson = Filters.equal("_id", id)

  def keepAlive(implicit hc: HeaderCarrier): Future[Unit] = {
    val sessionId = hc.sessionId.fold(throw NoSessionException)(_.value)
    collection
      .updateOne(
        filter = byId(sessionId),
        update = Updates.set("lastUpdated", Instant.now(clock))
      )
      .toFuture()
      .map(_ => (): Unit)
  }

  def get(implicit hc: HeaderCarrier): Future[UserAnswers] = {
    val sessionId            = hc.sessionId.fold(throw NoSessionException)(_.value)
    val retrievedUserAnswers = keepAlive.flatMap { _ =>
      collection
        .find(byId(sessionId))
        .headOption()
    }
    retrievedUserAnswers.map {
      case None     => UserAnswers(sessionId)
      case Some(ua) => ua
    }
  }

  def set(answers: UserAnswers): Future[Unit] = {
    val updatedAnswers = answers copy (lastUpdated = Instant.now(clock))

    collection
      .replaceOne(
        filter = byId(updatedAnswers.id),
        replacement = updatedAnswers,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => (): Unit)
  }

  def clear(implicit hc: HeaderCarrier): Future[Unit] = {
    val sessionId = hc.sessionId.fold(throw NoSessionException)(_.value)
    collection
      .deleteOne(byId(sessionId))
      .toFuture()
      .map(_ => (): Unit)
  }
}
