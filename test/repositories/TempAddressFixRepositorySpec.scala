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

import config.CryptoProvider
import models.tempAddressFix.AddressFixRecord
import testUtils.BaseSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import org.mongodb.scala.model.Filters.{equal => bsonEqual}
import java.time.temporal.ChronoUnit
import models.tempAddressFix.FixStatus

class TempAddressFixRepositorySpec extends BaseSpec with DefaultPlayMongoRepositorySupport[AddressFixRecord] {

  private val cryptoProvider: CryptoProvider = inject[CryptoProvider]

  override protected val repository: TempAddressFixRepository =
    new TempAddressFixRepository(mongoComponent, cryptoProvider)

  private val record1 =
    AddressFixRecord(nino = "nino1", postcode = "postcode 1", status = FixStatus.Todo)
  private val record2 =
    AddressFixRecord(nino = "nino2", postcode = "postcode 2", status = FixStatus.Todo)
  private val record3 =
    AddressFixRecord(nino = "nino3", postcode = "postcode 3", status = FixStatus.Todo)

  "insert" must {

    "insert a record and return it" in {
      val result = repository.insert(record1).futureValue
      result mustBe record1
      find(bsonEqual("key", record1.encryptedNino(cryptoProvider)))
    }

    "fail with an exception when inserting a duplicate key" in {
      val result = for {
        _        <- insert(record1)
        inserted <- repository.insert(record1)
      } yield inserted

      result.failed.futureValue mustBe a[Exception]
    }
  }

  "insertMany" must {

    "insert multiple records and return the count of inserted records" in {
      val result = repository.insertMany(Seq(record1, record2, record3)).futureValue
      result mustBe 3
    }

    "skip duplicates and return only the count of newly inserted records" in {
      val result = for {
        _        <- insert(record1)
        inserted <- repository.insertMany(Seq(record1, record2, record3))
      } yield inserted

      result.futureValue mustBe 2
    }
  }

  "findByKey" must {

    "return Some(record) when a matching record exists" in {
      insert(record1).futureValue

      val result = repository.findByKey("nino1").futureValue
      result mustBe Some(record1.copy(timestamp = record1.timestamp.truncatedTo(ChronoUnit.MILLIS)))
    }

    "return None when no matching record exists" in {
      val result = repository.findByKey("nino-does-not-exist").futureValue
      result mustBe None
    }
  }
}
