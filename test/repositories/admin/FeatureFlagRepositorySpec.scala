/*
 * Copyright 2023 HM Revenue & Customs
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

import models.admin.{AddressTaxCreditsBrokerCallToggle, FeatureFlag, FeatureFlagName, SingleAccountCheckToggle}
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.bson.BsonDocument
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global

class FeatureFlagRepositorySpec
    extends AnyWordSpec
    with GuiceOneAppPerSuite
    with Matchers
    with PatienceConfiguration
    with BeforeAndAfterEach
    with DefaultPlayMongoRepositorySupport[FeatureFlag] {

  override protected lazy val optSchema: Option[BsonDocument] = Some(BsonDocument("""
      { bsonType: "object"
      , required: [ "_id", "name", "isEnabled" ]
      , properties:
        { _id       : { bsonType: "objectId" }
        , name      : { bsonType: "string" }
        , isEnabled : { bsonType: "bool" }
        , description : { bsonType: "string" }
        }
      }
    """))

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .configure(Map("mongodb.uri" -> mongoUri))
    .build()

  override val checkTtlIndex = false

  lazy val repository: FeatureFlagRepository = app.injector.instanceOf[FeatureFlagRepository]

  "getFlag" must {
    "return None if there is no record" in {
      val result = repository.getFeatureFlag(AddressTaxCreditsBrokerCallToggle).futureValue

      result mustBe None
    }
  }

  "setFeatureFlag and getFeatureFlag" must {
    "insert and read a record in mongo" in {

      val result = (for {
        _      <- repository.setFeatureFlag(AddressTaxCreditsBrokerCallToggle, enabled = true)
        result <- findAll()
      } yield result).futureValue

      result mustBe List(
        FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true, AddressTaxCreditsBrokerCallToggle.description)
      )
    }
  }

  "setFeatureFlag" must {
    "replace a record not create a new one" in {
      val result = (for {
        _      <- repository.setFeatureFlag(AddressTaxCreditsBrokerCallToggle, enabled = true)
        _      <- repository.setFeatureFlag(AddressTaxCreditsBrokerCallToggle, enabled = false)
        result <- findAll()
      } yield result).futureValue

      result mustBe List(
        FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = false, AddressTaxCreditsBrokerCallToggle.description)
      )
    }
  }

  "setFeatureFlags" must {
    "set multiple records" in {
      val expectedFlags: Map[FeatureFlagName, Boolean] =
        Map(AddressTaxCreditsBrokerCallToggle -> false, SingleAccountCheckToggle -> true)
      val result                                       = (for {
        _      <- repository.setFeatureFlags(expectedFlags)
        result <- findAll()
      } yield result).futureValue

      result.sortBy(_.name.toString) mustBe expectedFlags
        .map { case (key, value) =>
          FeatureFlag(key, value, key.description)
        }
        .toList
        .sortBy(_.name.toString)
    }
  }

  "getAllFeatureFlags" must {
    "get a list of all the feature toggles" in {
      val allFlags: Seq[FeatureFlag] = (for {
        _      <- insert(
                    FeatureFlag(
                      AddressTaxCreditsBrokerCallToggle,
                      isEnabled = true,
                      AddressTaxCreditsBrokerCallToggle.description
                    )
                  )
        result <- repository.getAllFeatureFlags
      } yield result).futureValue

      allFlags mustBe List(
        FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true, AddressTaxCreditsBrokerCallToggle.description)
      )
    }
  }

  "deleteFeatureFlag" must {
    "delete a mongo record" in {
      val allFlags: Boolean = (for {
        _      <- insert(
                    FeatureFlag(
                      AddressTaxCreditsBrokerCallToggle,
                      isEnabled = true,
                      AddressTaxCreditsBrokerCallToggle.description
                    )
                  )
        result <- repository.deleteFeatureFlag(AddressTaxCreditsBrokerCallToggle)
      } yield result).futureValue

      allFlags mustBe true
      findAll().futureValue.length mustBe 0
    }
  }

  "Collection" must {
    "not allow duplicates" in {
      val result = intercept[MongoWriteException] {
        await(for {
          _ <- insert(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true))
          _ <- insert(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = false))
        } yield true)
      }
      result.getCode mustBe 11000
      result.getError.getMessage mustBe s"""E11000 duplicate key error collection: $databaseName.admin-feature-flags index: name dup key: { name: "$AddressTaxCreditsBrokerCallToggle" }"""
    }
  }
}
