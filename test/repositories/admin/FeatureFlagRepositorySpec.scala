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

import models.admin.{AddressTaxCreditsBrokerCallToggle, FeatureFlag}
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.bson.BsonDocument
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import testUtils.{BaseSpec, FeatureFlagRepositoryUtils}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

class FeatureFlagRepositorySpec extends BaseSpec with DefaultPlayMongoRepositorySupport[FeatureFlag] {

  override protected lazy val optSchema = Some(BsonDocument("""
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

  override implicit lazy val app = localGuiceApplicationBuilder()
    .configure(Map("mongodb.uri" -> mongoUri) ++ configValues)
    .build()

  lazy val repository = app.injector.instanceOf[FeatureFlagRepository]

  "getFlag" must {
    "return None if there is no record" in {
      val result = repository.getFeatureFlag(AddressTaxCreditsBrokerCallToggle).futureValue

      result mustBe None
    }
  }

  "setFeatureFlag and getFeatureFlag" must {
    "insert and read a record in mongo" in {

      val result = (for {
        _      <- repository.setFeatureFlag(AddressTaxCreditsBrokerCallToggle, true)
        result <- repository.getFeatureFlag(AddressTaxCreditsBrokerCallToggle)
      } yield result).futureValue

      result mustBe Some(
        FeatureFlag(AddressTaxCreditsBrokerCallToggle, true, AddressTaxCreditsBrokerCallToggle.description)
      )

    }
  }

  "setFeatureFlag" must {
    "replace a record not create a new one" in {
      val result = (for {
        _      <- repository.setFeatureFlag(AddressTaxCreditsBrokerCallToggle, true)
        _      <- repository.setFeatureFlag(AddressTaxCreditsBrokerCallToggle, false)
        result <- repository.getFeatureFlag(AddressTaxCreditsBrokerCallToggle)
      } yield result).futureValue

      result mustBe Some(
        FeatureFlag(AddressTaxCreditsBrokerCallToggle, false, AddressTaxCreditsBrokerCallToggle.description)
      )

    }
  }

  "getAllFeatureFlags" must {
    "get a list of all the feature toggles" in {
      lazy val adminRepositoryUtils = app.injector.instanceOf[FeatureFlagRepositoryUtils]

      val allFlags: Seq[FeatureFlag] = (for {
        _      <- adminRepositoryUtils.setFeatureFlag(AddressTaxCreditsBrokerCallToggle, true)
        result <- repository.getAllFeatureFlags
      } yield result).futureValue

      allFlags mustBe List(
        FeatureFlag(AddressTaxCreditsBrokerCallToggle, true, AddressTaxCreditsBrokerCallToggle.description)
      )
    }
  }

  "Collection" must {
    "not allow duplicates" in {
      lazy val adminRepositoryUtils = app.injector.instanceOf[FeatureFlagRepositoryUtils]

      val result = intercept[MongoWriteException] {
        await((for {
          _ <- adminRepositoryUtils.insertFeatureFlag(AddressTaxCreditsBrokerCallToggle, true)
          _ <- adminRepositoryUtils.insertFeatureFlag(AddressTaxCreditsBrokerCallToggle, false)
        } yield true))
      }
      result.getCode mustBe 11000
      println(result.getError.getMessage)
      result.getError.getMessage mustBe s"""E11000 duplicate key error collection: $databaseName.admin-feature-flags index: name dup key: { name: "$AddressTaxCreditsBrokerCallToggle" }"""
//      "result.getError.getMessage" mustBe s"""E11000 duplicate key error collection: $databaseName.admin-feature-flags index: name dup key: { name: "$AddressTaxCreditsBrokerCallToggle" }"""

    }
  }
}
