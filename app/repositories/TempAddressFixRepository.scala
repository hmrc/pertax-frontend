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
import config.CryptoProvider
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import uk.gov.hmrc.mongo.MongoComponent
import models.tempAddressFix.AddressFixRecord
import org.mongodb.scala.model.Filters.equal
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

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
        IndexModel(ascending("key"), IndexOptions().unique(true).name("key"))
      )
    ) {

  def insert(entity: AddressFixRecord): Future[AddressFixRecord] =
    collection
      .insertOne(entity)
      .toFuture()
      .map(_ => entity)

  def findByKey(key: String): Future[Option[AddressFixRecord]] =
    collection
      .find(equal("key", AddressFixRecord.hash(key)))
      .headOption()

}
