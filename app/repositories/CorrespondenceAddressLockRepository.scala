/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.zone.ZoneRules
import java.time.{OffsetDateTime, ZoneId, ZoneOffset}
import java.util.TimeZone

import com.google.inject.{Inject, Singleton}
import models.AddressJourneyTTLModel
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.BSONDocumentWrites
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CorrespondenceAddressLockRepository @Inject()(mongo: ReactiveMongoApi, implicit val ec: ExecutionContext) {

  private val collectionName: String = "correspondenceAddressLock"

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  import CorrespondenceAddressLockRepository._

  def insert(nino: String): Future[Boolean] =
    insertCore(nino, getNextMidnight).map(_.ok) recover {
      case e: DatabaseException if e.getMessage().contains("E11000 duplicate key error collection") => false
    }

  def get(nino: String): Future[Option[AddressJourneyTTLModel]] =
    getCore(
      BSONDocument(
        BSONDocument("_id"     -> nino),
        BSONDocument(EXPIRE_AT -> BSONDocument("$gt" -> toBSONDateTime(OffsetDateTime.now()))))
    )

  private[repositories] def getCore[S](selector: BSONDocument): Future[Option[AddressJourneyTTLModel]] =
    this.collection.flatMap(_.find(selector, None).one[AddressJourneyTTLModel])

  private[repositories] def insertCore(nino: String, date: OffsetDateTime): Future[WriteResult] =
    this.collection.flatMap(_.insert(ordered = false).one(AddressJourneyTTLModel(nino, toBSONDateTime(date))))

  private[repositories] def drop(implicit ec: ExecutionContext): Future[Boolean] =
    for {
      result <- this.collection.flatMap(_.drop(failIfNotFound = false))
      _      <- setIndex()
    } yield result

  private[repositories] lazy val ttlIndex = Index(
    Seq((EXPIRE_AT, IndexType.Ascending)),
    name = Some("ttlIndex"),
    unique = false,
    background = false,
    dropDups = false,
    sparse = false,
    options = BSONDocument("expireAfterSeconds" -> 0)
  )

  private[repositories] def removeIndex(): Future[Int] =
    for {
      list <- collection.flatMap(_.indexesManager.list())
      count <- ttlIndex.name match {
                case Some(name) if list.exists(_.name contains name) =>
                  collection.flatMap(_.indexesManager.drop(name))
                case _ =>
                  Future.successful(0)
              }
    } yield count

  private[repositories] def setIndex(): Future[Boolean] =
    for {
      _      <- removeIndex()
      result <- collection.flatMap(_.indexesManager.ensure(ttlIndex))
    } yield result

  private[repositories] def isTtlSet: Future[Boolean] =
    for {
      list <- this.collection.flatMap(_.indexesManager.list())
    } yield list.exists(_.name == ttlIndex.name)

  val started: Future[Unit] = setIndex().map(_ => ())

}

object CorrespondenceAddressLockRepository {
  val EXPIRE_AT = "expireAt"
  val UK_TIME_ZONE: ZoneId = TimeZone.getTimeZone("Europe/London").toZoneId
  val UK_ZONE_Rules: ZoneRules = UK_TIME_ZONE.getRules
  val GMT_OFFSET: ZoneOffset = ZoneOffset.ofHours(0)
  val BST_OFFSET: ZoneOffset = ZoneOffset.ofHours(1)

  def toBSONDateTime(dateTime: OffsetDateTime): BSONDateTime =
    BSONDateTime(dateTime.toInstant.toEpochMilli)

  def getNextMidnight: OffsetDateTime = getNextMidnight(OffsetDateTime.now())

  private def nextUTCMidnightInUKDateTime(offsetDateTime: OffsetDateTime): OffsetDateTime = {
    val utcNextDay = offsetDateTime.withOffsetSameInstant(GMT_OFFSET).plusDays(1)
    utcNextDay.toLocalDate.atStartOfDay.atZone(UK_TIME_ZONE).toOffsetDateTime
  }

  // SE-125, we are unable to determine what time zone the NPS services would use during British Summer Time.
  // So the lock needs to be safe for both scenarios during BST.
  // A safe guard is put in place so that if the user comes in between 11 UTC+0 and midnight UTC+0 we would lock them till
  // midnight UTC+0 of the next day in case NPS resets at midnight UTC+1 instead of midnight UTC+0.
  private[repositories] def getNextMidnight(offsetDateTime: OffsetDateTime): OffsetDateTime = {
    val ukDateTime = offsetDateTime.atZoneSameInstant(UK_TIME_ZONE)
    val utcMidnightInUkDateTime = nextUTCMidnightInUKDateTime(offsetDateTime)
    ukDateTime.getOffset match {
      case BST_OFFSET if ukDateTime.getHour == 0 =>
        utcMidnightInUkDateTime.getOffset match {
          case GMT_OFFSET => utcMidnightInUkDateTime
          case _          => utcMidnightInUkDateTime.plusDays(1).withOffsetSameInstant(BST_OFFSET)
        }
      case BST_OFFSET =>
        utcMidnightInUkDateTime.getOffset match {
          case GMT_OFFSET => utcMidnightInUkDateTime
          case _          => utcMidnightInUkDateTime.plusHours(1).withOffsetSameInstant(BST_OFFSET)
        }
      case _ =>
        utcMidnightInUkDateTime
    }
  }

}
