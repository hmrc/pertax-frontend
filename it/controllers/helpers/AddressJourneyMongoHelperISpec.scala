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

import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.TimeZone

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.util.Random


class AddressJourneyMongoHelperISpec extends UnitSpec
  with GuiceOneAppPerSuite
  with PatienceConfiguration
  with BeforeAndAfterEach {

  def mongo: AddressJourneyMongoHelper = app.injector.instanceOf[AddressJourneyMongoHelper]

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

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

  "AddressJourneyMongoHelper.getNextUKMidnight" when {
    import AddressJourneyMongoHelper._
    "the public function is called without a parameter" should {
      "return midnight of today" in {
        val now = OffsetDateTime.now()
        val tomorrow = now.plusDays(1)
        val london: TimeZone = TimeZone.getTimeZone("Europe/London")
        val instantInUK = tomorrow.toInstant.atZone(london.toZoneId)

        val expected = instantInUK.toLocalDate.atStartOfDay().atZone(london.toZoneId).toOffsetDateTime

        getNextUKMidnight shouldBe expected
      }
    }
    "the private function is called with a parameter" should {
      "return the next UK midnight of that day" when {
        "the time is 1 nanosecond before midnight" in {
          val testDate = LocalDateTime.of(2018, 12, 31, 23, 59, 59, 999)
          val offset = UK_TIME_ZONE.getRules.getOffset(testDate)
          val justBeforeNewYearInUK = testDate.atOffset(offset)

          val uKNewYear = getNextUKMidnight(justBeforeNewYearInUK)

          uKNewYear.toLocalDateTime shouldBe LocalDateTime.of(2019, 1, 1, 0, 0, 0, 0)
          uKNewYear.getOffset shouldBe offset
        }
        "the time is exactly midnight" in {
          val testDate = LocalDateTime.of(2018, 12, 31, 0, 0, 0, 0)
          val offset = UK_TIME_ZONE.getRules.getOffset(testDate)
          val newYearsEve = testDate.atOffset(offset)

          val uKNewYear = getNextUKMidnight(newYearsEve)

          uKNewYear.toLocalDateTime shouldBe LocalDateTime.of(2019, 1, 1, 0, 0, 0, 0)
          uKNewYear.getOffset shouldBe offset
        }
        "the time is exactly 1 am and daylight saving (DST) starts at 1 am the next day" in {
          val testDate = LocalDateTime.of(2019, 3, 30, 1, 0, 0, 0)
          val offset = UK_TIME_ZONE.getRules.getOffset(testDate)
          val aDayBeforeDst = testDate.atOffset(offset)

          offset shouldBe ZoneOffset.ofHours(0)
          LocalDateTime.of(2019, 3, 31, 1, 0, 0, 0).atZone(UK_TIME_ZONE).getOffset shouldBe ZoneOffset.ofHours(1)

          val anHourBeforeDst = getNextUKMidnight(aDayBeforeDst)

          anHourBeforeDst.toLocalDateTime shouldBe LocalDateTime.of(2019, 3, 31, 0, 0, 0, 0)
          anHourBeforeDst.getOffset shouldBe offset
        }
        "the time is exactly 2 am and daylight saving (DST) ends at 2 am the next day" in {
          val testDate = LocalDateTime.of(2018, 10, 27, 2, 0, 0, 0)
          val offset = UK_TIME_ZONE.getRules.getOffset(testDate)
          val aDayBeforeWinterTime = testDate.atOffset(offset)

          offset shouldBe ZoneOffset.ofHours(1)
          LocalDateTime.of(2018, 10, 28, 2, 0, 0, 0).atZone(UK_TIME_ZONE).getOffset shouldBe ZoneOffset.ofHours(0)

          val twoHoursBeforeDstEnds = getNextUKMidnight(aDayBeforeWinterTime)

          twoHoursBeforeDstEnds.toLocalDateTime shouldBe LocalDateTime.of(2018, 10, 28, 0, 0, 0, 0)
          twoHoursBeforeDstEnds.getOffset shouldBe offset
        }
      }
    }
  }

  "setIndex" when {
    "ensure the index is set" in {
      await(mongo.removeIndex())
      await(mongo.isTtlSet) shouldBe false

      await(mongo.setIndex())
      await(mongo.isTtlSet) shouldBe true
    }
  }

  "get" when {
    "there isn't an existing record" should {
      "return None" in {
        val fGet = mongo.get(testNino)
        await(fGet) shouldBe None
      }
    }
    "there isn't an existing record that matches the requested nino" should {
      "return None" in {
        val currentTime = System.currentTimeMillis()
        await(mongo.insertCore(differentNino, OffsetDateTime.now()))

        val fGet = mongo.get(testNino)
        await(fGet) shouldBe None
      }
    }
    "there is an existing record and it has not yet expired" should {
      "return the record" in {
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
      "return None" in {
        await(mongo.insertCore(testNino, OffsetDateTime.now()))
        val fGet = mongo.get(testNino)
        await(fGet) shouldBe None
      }
    }
  }

  "insert" when {
    "there isn't an existing record" should {
      "return true" in {
        import AddressJourneyMongoHelper._
        val midnight = toBSONDateTime(getNextUKMidnight)
        val result = await(mongo.insert(testNino))
        result shouldBe true

        val inserted = await(mongo.get(testNino))
        inserted shouldBe defined
        inserted.get.nino shouldBe testNino
        inserted.get.expireAt shouldBe midnight
      }
    }
    "there is an existing record" should {
      "return false" in {
        await(mongo.insert(testNino))
        val result = await(mongo.insert(testNino))
        result shouldBe false
      }
    }
  }

}
