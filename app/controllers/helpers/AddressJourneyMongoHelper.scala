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

package controllers.helpers

import java.util.Date

import javax.inject.Inject
import models.AddressJourneyTTLModel
import models.AddressJourneyTTLModel._
import org.joda.time.{DateTime, LocalDate}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.{MongoDbConnection, ReactiveMongoComponent}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.json.JSONSerializationPack.Writer
import uk.gov.hmrc.mongo.ReactiveRepository
import play.modules.reactivemongo.JSONFileToSave._

import scala.concurrent.{ExecutionContext, Future}
import reactivemongo.api.indexes.{Index, IndexType}

import scala.concurrent.ExecutionContext.Implicits.global


class AddressJourneyMongoHelper @Inject() (mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext) extends ReactiveRepository[AddressJourneyTTLModel, BSONObjectID](
  "addressUpdatedFlag",
  mongo.mongoConnector.db,
  AddressJourneyTTLModel.format
) with MongoDbConnection {

  val ttlSeconds: Long = 30

  def insertIndex(flag: Boolean)(implicit ec: ExecutionContext) = {
    this.collection.insert(
      AddressJourneyTTLModel(flag, BSONDateTime(new Date("March 7, 2019 14:24:00").getTime))
    )
  }

  import reactivemongo.api.indexes.{Index, IndexType}

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("expireAt", IndexType.Ascending)), name = Some("expireTime"), unique = true, sparse = true)
  )

//  def insertIndex(flag: Boolean): Future[WriteResult] = {
//    collection.insert(AddressJourneyTTLModel(flag, new Date("March 7, 2019 13:52:00")))
//  }
//
//  private lazy val ttlIndex = Index(
//    Seq(("key", IndexType(Ascending.value))),
//    name = Some("expireTime"),
//    options = BSONDocument("expireAt" -> 1, "expireAfterSeconds" -> 0)
//  )
//
//  private def setIndex(): Unit = {
//    collection.indexesManager.drop(ttlIndex.name.get) onComplete {
//      _ => collection.indexesManager.ensure(ttlIndex)
//    }
//  }
//
//  setIndex()
}
