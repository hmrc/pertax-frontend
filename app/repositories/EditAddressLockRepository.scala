/*
 * Copyright 2022 HM Revenue & Customs
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
import java.time.{Instant, OffsetDateTime, ZoneId, ZoneOffset}
import java.util.TimeZone
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import controllers.bindable.AddrType
import models.{AddressJourneyTTLModel, EditedAddress}
import org.mongodb.scala.{DuplicateKeyException, MongoException}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model._
import org.mongodb.scala.result.InsertOneResult
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import repositories.EditAddressLockRepository.EXPIRE_AT
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EditAddressLockRepository @Inject() (
  configDecorator: ConfigDecorator,
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[AddressJourneyTTLModel](
      collectionName = "EditAddressLock",
      mongoComponent = mongoComponent,
      domainFormat = AddressJourneyTTLModel.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending(EXPIRE_AT),
          IndexOptions()
            .name("ttlIndex")
            .expireAfter(configDecorator.editAddressTtl, TimeUnit.SECONDS)
        ),
        IndexModel(
          Indexes.ascending("nino", "editedAddress.addressType"),
          IndexOptions()
            .unique(true)
            .name("ninoAddressIndex")
        )
      )
    ) with Logging {

  import EditAddressLockRepository._

  def insert(nino: String, addressType: AddrType): Future[Boolean] = {

    val nextMidnight = getNextMidnight(Instant.now().atOffset(ZoneOffset.UTC)).toInstant

    val record: EditedAddress =
      AddrType.toEditedAddress(addressType, nextMidnight)

    insertCore(AddressJourneyTTLModel(nino, record)).map(_.wasAcknowledged()) recover {
      case e: MongoException =>
        val errorCode = e.getCode
        logger.error(s"Edit address lock failure with error $errorCode")
        false
      case e: DuplicateKeyException =>
        val errorCode = e.getCode
        logger.error(s"Edit address lock failure with error $errorCode")
        false
    }
  }

  def get(nino: String): Future[List[AddressJourneyTTLModel]] =
    collection
      .find(getCore(nino))
      .toFuture()
      .map(_.toList.filter(_.editedAddress.expireAt.toEpochMilli > Instant.now().toEpochMilli))

  private def getCore(nino: String): Bson =
    Filters.equal("nino", nino)

  def insertCore(record: AddressJourneyTTLModel): Future[InsertOneResult] =
    collection.insertOne(record).toFuture()
}

object EditAddressLockRepository {
  val EXPIRE_AT = "editedAddress.expireAt"
  val UK_TIME_ZONE: ZoneId = TimeZone.getTimeZone("Europe/London").toZoneId
  val UK_ZONE_Rules: ZoneRules = UK_TIME_ZONE.getRules
  val GMT_OFFSET: ZoneOffset = ZoneOffset.ofHours(0)
  val BST_OFFSET: ZoneOffset = ZoneOffset.ofHours(1)

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
