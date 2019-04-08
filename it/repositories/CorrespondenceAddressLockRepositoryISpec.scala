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

import models.PertaxContext
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.util.Random

class CorrespondenceAddressLockRepositoryISpec extends UnitSpec
  with GuiceOneAppPerSuite
  with PatienceConfiguration
  with BeforeAndAfterEach {

  def mongo: CorrespondenceAddressLockRepository = app.injector.instanceOf[CorrespondenceAddressLockRepository]

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit lazy val hc: HeaderCarrier = app.injector.instanceOf[HeaderCarrier]
  implicit lazy val context: PertaxContext = app.injector.instanceOf[PertaxContext]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(mongo.drop)
  }

  private val generator = new Generator(new Random())

  private val testNino: Nino = generator.nextNino
  private val differentNino: Nino = {
    @scala.annotation.tailrec
    def next(nino: Nino): Nino = generator.nextNino match {
      case `testNino` => next(nino)
      case _ => nino
    }

    next(testNino)
  }

  "setIndex" when {
    "ensure the index is set" ignore {
      await(mongo.removeIndex())
      await(mongo.isTtlSet) shouldBe false

      await(mongo.setIndex())
      await(mongo.isTtlSet) shouldBe true
    }
  }

  "get" when {
    "there isn't an existing record" should {
      "return None" ignore {
        val fGet = mongo.get(testNino)

        await(fGet) shouldBe None
      }
    }
    "there isn't an existing record that matches the requested nino" should {
      "return None" ignore {
        await(mongo.insertCore(differentNino, OffsetDateTime.now()))

        val fGet = mongo.get(testNino)

        await(fGet) shouldBe None
      }
    }
    "there is an existing record and it has not yet expired" should {
      "return the record" ignore {
        val currentTime = System.currentTimeMillis()
        val timeOffSet = 10L

        await(mongo.insertCore(testNino, OffsetDateTime.now().plusSeconds(timeOffSet)))

        val fGet = mongo.get(testNino)
        val inserted = await(mongo.getCore(BSONDocument()))
        currentTime should be < inserted.get.expireAt.value
        await(fGet) shouldBe inserted
      }
    }
    "there is an existing record but has expired" should {
      "return None" ignore {
        await(mongo.insertCore(testNino, OffsetDateTime.now()))

        val fGet = mongo.get(testNino)

        await(fGet) shouldBe None
      }
    }
  }

  "insert" when {
    "there isn't an existing record" should {
      "return true" ignore {
        import CorrespondenceAddressLockRepository._
        val midnight = toBSONDateTime(getNextMidnight)
        val result = await(mongo.insert(testNino))
        result shouldBe true

        val inserted = await(mongo.get(testNino))

        inserted shouldBe defined
        inserted.get.nino shouldBe testNino
        inserted.get.expireAt shouldBe midnight
      }
    }
    "there is an existing record" should {
      "return false" ignore {
        await(mongo.insert(testNino))

        val result = await(mongo.insert(testNino))

        result shouldBe false
      }
    }
  }

}
