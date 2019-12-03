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

import java.time.OffsetDateTime

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.util.Random

class CachingItSpec extends UnitSpec
  with GuiceOneAppPerSuite
  with PatienceConfiguration
  with BeforeAndAfterEach {

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  def mongo: CorrespondenceAddressLockRepository = app.injector.instanceOf[CorrespondenceAddressLockRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(mongo.drop)
  }

  private val generator = new Generator(new Random())

  private val testNino: Nino = generator.nextNino
  private val differentNino: Nino = {
    @scala.annotation.tailrec
    def next(): Nino = generator.nextNino match {
      case `testNino` => next()
      case newNino => newNino
    }

    next()
  }

  "CorrespondenceAddressLockRepository" when {

    "setIndex is called" should {
      "ensure the index is set" in {
        await(mongo.removeIndex())
        await(mongo.isTtlSet) shouldBe false

        await(mongo.setIndex())
        await(mongo.isTtlSet) shouldBe true
      }
    }

    "get is called" should {
      "return None" when {
        "there isn't an existing record" in {
          val fGet = mongo.get(testNino.withoutSuffix)

          await(fGet) shouldBe None
        }

        "there isn't an existing record that matches the requested nino" in {
          val timeOffSet = 10L
          await(mongo.insertCore(differentNino.withoutSuffix, OffsetDateTime.now().plusSeconds(timeOffSet)))

          val fGet = mongo.get(testNino.withoutSuffix)

          await(fGet) shouldBe None

        }

        "there is an existing record but has expired" in {
          await(mongo.insertCore(testNino.withoutSuffix, OffsetDateTime.now()))

          val fGet = mongo.get(testNino.withoutSuffix)

          await(fGet) shouldBe None
        }
      }

      "return the record" when {
        "there is an existing record and it has not yet expired" in {
          val currentTime = System.currentTimeMillis()
          val timeOffSet = 10L

          await(mongo.insertCore(testNino.withoutSuffix, OffsetDateTime.now().plusSeconds(timeOffSet)))

          val fGet = mongo.get(testNino.withoutSuffix)
          val inserted = await(mongo.getCore(BSONDocument()))
          currentTime should be < inserted.get.expireAt.value
          await(fGet) shouldBe inserted
        }
      }
    }

    "insert is called" should {
      "return true" when {
        "there isn't an existing record" in {
          import CorrespondenceAddressLockRepository._
          val offsetTime = getNextMidnight(OffsetDateTime.now())
          val midnight = toBSONDateTime(offsetTime)
          val result = await(mongo.insert(testNino.withoutSuffix))
          result shouldBe true

          val inserted = await(mongo.get(testNino.withoutSuffix))

          inserted shouldBe defined
          inserted.get.nino shouldBe testNino.withoutSuffix
          inserted.get.expireAt shouldBe midnight
        }
      }
      "return false" when {
        "there is an existing record" in {
          await(mongo.insert(testNino.withoutSuffix))

          val result = await(mongo.insert(testNino.withoutSuffix))

          result shouldBe false
        }
      }
    }
  }

}
