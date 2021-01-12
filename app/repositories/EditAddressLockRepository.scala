/*
 * Copyright 2021 HM Revenue & Customs
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
import config.ConfigDecorator
import controllers.bindable.AddrType
import models.{AddressJourneyTTLModel, EditedAddress}
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.Cursor
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.BSONDocumentWrites
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EditAddressLockRepository @Inject()(
  configDecorator: ConfigDecorator
)(mongo: ReactiveMongoApi, implicit val ec: ExecutionContext) {

  private val collectionName: String = "EditAddressLock"
  private val duplicateKeyErrorCode = "E11000"
  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  private val logger = Logger(this.getClass)

  import EditAddressLockRepository._

  def insert(nino: String, addressType: AddrType): Future[Boolean] = {

    val record: EditedAddress =
      AddrType.toEditedAddress(addressType, toBSONDateTime(getNextMidnight(OffsetDateTime.now())))

    insertCore(AddressJourneyTTLModel(nino, record)).map(_.ok) recover {
      case e: DatabaseException => {
        val errorCode = e.code.getOrElse("unknown code")
        logger.error(s"Edit address lock failure with error $errorCode")
        false
      }
    }
  }

  def get(nino: String): Future[List[AddressJourneyTTLModel]] =
    getCore(
      BSONDocument(
        BSONDocument("nino" -> nino),
        BSONDocument(
          BSONDocument(EXPIRE_AT -> BSONDocument("$gt" -> toBSONDateTime(OffsetDateTime.now())))
        )
      )
    )

  private[repositories] def getCore(selector: BSONDocument): Future[List[AddressJourneyTTLModel]] =
    this.collection
      .flatMap {
        _.find(selector, None)
          .cursor[AddressJourneyTTLModel]()
          .collect[List](
            5,
            Cursor.FailOnError[List[AddressJourneyTTLModel]]()
          )
      }
      .recover {
        case e: Exception =>
          Logger.error(s"Unable to find document: ${e.getMessage}")
          List[AddressJourneyTTLModel]()
      }

  private[repositories] def insertCore(record: AddressJourneyTTLModel): Future[WriteResult] =
    this.collection.flatMap(coll => coll.insert(ordered = false).one(record))

  private[repositories] def drop(implicit ec: ExecutionContext): Future[Boolean] =
    for {
      result <- this.collection.flatMap(_.drop(failIfNotFound = false))
      _      <- setIndex()
    } yield result

  private val ttl = configDecorator.editAddressTtl

  private[repositories] lazy val ttlIndex = Index(
    Seq((EXPIRE_AT, IndexType.Ascending)),
    name = Some("ttlIndex"),
    unique = false,
    background = false,
    dropDups = false,
    sparse = false,
    options = BSONDocument("expireAfterSeconds" -> ttl)
  )

  private[repositories] lazy val editAddressIndex =
    Index(Seq(("nino", IndexType.Ascending), ("editedAddress.addressType", IndexType.Ascending)), unique = true)

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
      _          <- removeIndex()
      ttlResult  <- collection.flatMap(_.indexesManager.ensure(ttlIndex))
      editResult <- collection.flatMap(_.indexesManager.ensure(editAddressIndex))
    } yield ttlResult && editResult

  private[repositories] def isTtlSet: Future[Boolean] =
    for {
      list <- this.collection.flatMap(_.indexesManager.list())
    } yield list.exists(_.name == ttlIndex.name)

  val started: Future[Unit] = setIndex().map(_ => ())

}

object EditAddressLockRepository {
  val EXPIRE_AT = "editedAddress.expireAt"
  val UK_TIME_ZONE: ZoneId = TimeZone.getTimeZone("Europe/London").toZoneId
  val UK_ZONE_Rules: ZoneRules = UK_TIME_ZONE.getRules
  val GMT_OFFSET: ZoneOffset = ZoneOffset.ofHours(0)
  val BST_OFFSET: ZoneOffset = ZoneOffset.ofHours(1)

  def toBSONDateTime(dateTime: OffsetDateTime): BSONDateTime =
    BSONDateTime(dateTime.toInstant.toEpochMilli)

  private def nextUTCMidnightInUKDateTime(offsetDateTime: OffsetDateTime): OffsetDateTime = {
    val utcNextDay = offsetDateTime.withOffsetSameInstant(GMT_OFFSET).plusDays(1)
    utcNextDay.toLocalDate.atStartOfDay.atZone(UK_TIME_ZONE).toOffsetDateTime
  }

  // SE-125, we are unable to determine what time zone the NPS services would use during British Summer Time.
  // So the lock needs to be safe for both scenarios during BST.
  // A safe guard is put in place so that if the user comes in between 11 UTC+0 and midnight UTC+0 we would lock them till
  // midnight UTC+0 of the next day in case NPS resets at midnight UTC+1 instead of midnight UTC+0.
  def getNextMidnight(offsetDateTime: OffsetDateTime): OffsetDateTime = {
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
