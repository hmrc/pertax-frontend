/*
 * Copyright 2026 HM Revenue & Customs
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

import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes
import config.CryptoProvider
import org.mongodb.scala.model.{IndexModel, IndexOptions, InsertManyOptions}
import uk.gov.hmrc.mongo.MongoComponent
import models.tempAddressFix.{AddressFixRecord, FixStatus}
import org.mongodb.scala.MongoBulkWriteException
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates
import org.mongodb.scala.model.{FindOneAndUpdateOptions, ReturnDocument}
import play.api.Logging
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.jdk.CollectionConverters.*
import java.util.concurrent.TimeUnit
import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TempAddressFixRepository @Inject() (
  mongoComponent: MongoComponent,
  cryptoProvider: CryptoProvider
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[AddressFixRecord](
      mongoComponent = mongoComponent,
      collectionName = "fixNinoAddresses",
      domainFormat = AddressFixRecord.format(cryptoProvider),
      indexes = Seq(
        IndexModel(ascending("data.nino"), IndexOptions().unique(true).name("nino")),
        IndexModel(
          Indexes.ascending("data.timestamp"),
          IndexOptions()
            .name("ttlIndex")
            .expireAfter(365, TimeUnit.DAYS)
            .background(true)
        )
      )
    )
    with Logging {

  def insert(entity: AddressFixRecord): Future[AddressFixRecord] =
    collection
      .insertOne(entity)
      .toFuture()
      .map(_ => entity)

  def insertMany(entities: Seq[AddressFixRecord]): Future[Int] = {
    val DUPLICATE_KEY_ERROR = 11000

    collection
      .insertMany(entities, InsertManyOptions().ordered(false))
      .toFuture()
      .map { multiBulkWriteResult =>
        logger.info(
          s"Number of records requested to be inserted: ${entities.size}"
        )
        multiBulkWriteResult.getInsertedIds.size()
      }
      .recoverWith {
        case e: MongoBulkWriteException if e.getWriteErrors.asScala.forall(_.getCode == DUPLICATE_KEY_ERROR) =>
          val inserted = e.getWriteResult.getInsertedCount
          val skipped  = e.getWriteErrors.asScala.count(_.getCode == DUPLICATE_KEY_ERROR)
          logger.info(
            s"Records requested: ${entities.size}, inserted: $inserted, skipped (duplicates): $skipped"
          )
          Future.successful(inserted)
      }
  }

  def findByKey(key: String): Future[Option[AddressFixRecord]] =
    collection
      .find(equal("data.nino", AddressFixRecord.encryptedNino(key, cryptoProvider)))
      .headOption()

  def findAll: Future[Seq[AddressFixRecord]] =
    collection
      .find()
      .toFuture()

  def findTodo: Future[Option[AddressFixRecord]] =
    collection
      .find(equal("data.status", FixStatus.Todo.toString))
      .limit(1)
      .headOption()

  def findNRecords(size: Int, status: FixStatus): Future[Seq[AddressFixRecord]] =
    collection
      .find(equal("data.status", status.toString))
      .limit(size)
      .toFuture()

  def findOneAndUpdate(
    key: String,
    newStatus: FixStatus,
    oldStatus: Option[FixStatus] = None
  ): Future[Option[AddressFixRecord]] = {
    val filter =
      oldStatus.fold(equal("data.nino", AddressFixRecord.encryptedNino(key, cryptoProvider))) { status =>
        and(
          equal("data.nino", AddressFixRecord.encryptedNino(key, cryptoProvider)),
          equal("data.status", status.toString)
        )
      }

    collection
      .findOneAndUpdate(
        filter,
        Updates.combine(
          Updates.set("data.status", newStatus.toString),
          Updates.set("data.timestamp", Instant.now())
        ),
        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      )
      .toFutureOption()
  }

}
