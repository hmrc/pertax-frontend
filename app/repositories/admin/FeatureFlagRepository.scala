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

package repositories.admin

import models.admin.FeatureFlagName.allFeatureFlags
import models.admin.{FeatureFlag, FeatureFlagName, TaxcalcToggle}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FeatureFlagRepository @Inject() (
  val mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[FeatureFlag](
      collectionName = "admin-feature-flags",
      mongoComponent = mongoComponent,
      domainFormat = FeatureFlag.format,
      indexes = Seq(
        IndexModel(
          keys = Indexes.ascending("name"),
          indexOptions = IndexOptions()
            .name("name")
            .unique(true)
        )
      ),
      extraCodecs = Codecs.playFormatSumCodecs(FeatureFlagName.formats)
    )
    with Transactions {

  private implicit val tc = TransactionConfiguration.strict

  def getFeatureFlag(name: FeatureFlagName): Future[Option[FeatureFlag]] =
    collection
      .find(Filters.equal("name", name.toString))
      .headOption()

  def getAllFeatureFlags: Future[List[FeatureFlag]] =
    collection
      .find()
      .toFuture()
      .map(_.toList)

  def setFeatureFlag(name: FeatureFlagName, enabled: Boolean): Future[Boolean] =
    collection
      .replaceOne(
        filter = equal("name", name),
        replacement = FeatureFlag(name, enabled, name.description),
        options = ReplaceOptions().upsert(true)
      )
      .map(_.wasAcknowledged())
      .toSingle()
      .toFuture()

  def setFeatureFlags(flags: Map[FeatureFlagName, Boolean]): Future[Unit] = {
    val featureFlags = flags.map { case (flag, status) =>
      FeatureFlag(flag, status, flag.description)
    }.toList
    withSessionAndTransaction(session =>
      for {
        _ <- collection.deleteMany(session, filter = in("name", flags.keys.toSeq: _*)).toFuture()
        _ <- collection.insertMany(session, featureFlags).toFuture()
      } yield ()
    )
  }
}
