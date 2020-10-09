/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.TimeZone

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.bindable.{MainAddrType, PostalAddrType, PrimaryAddrType, SoleAddrType}
import models.{AddressJourneyTTLModel, EditCorrespondenceAddress, EditPrimaryAddress, EditSoleAddress}
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONDateTime
import uk.gov.hmrc.domain.{Generator, Nino}
import util.BaseSpec
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class EditAddressLockRepositorySpec extends BaseSpec with MockitoSugar {

  val MAX_NANO_SECONDS = 999999

  val testNino = Nino(new Generator(new Random()).nextNino.nino)

  val soleLock = AddressJourneyTTLModel(testNino.toString(), EditSoleAddress(BSONDateTime(11111111)))
  val primaryLock = AddressJourneyTTLModel(testNino.toString(), EditPrimaryAddress(BSONDateTime(11111111)))
  val correspondenceLock =
    AddressJourneyTTLModel(testNino.toString(), EditCorrespondenceAddress(BSONDateTime(11111111)))

  def userRequest[A]: UserRequest[A] =
    buildUserRequest(nino = Some(testNino), request = FakeRequest().asInstanceOf[Request[A]])

  def sut(locks: List[AddressJourneyTTLModel]): EditAddressLockRepository =
    new EditAddressLockRepository(injected[ConfigDecorator])(injected[ReactiveMongoApi], injected[ExecutionContext]) {
      override def get(nino: String): Future[List[AddressJourneyTTLModel]] =
        Future.successful(locks)
    }

  "AddressJourneyMongoHelper.getNextMidnight" when {
    import EditAddressLockRepository._
    "called with the date parameter" should {
      "return midnight of a day during GMT" in {

        val now = OffsetDateTime.of(
          LocalDateTime.of(2019, 1, 14, 14, 30),
          GMT_OFFSET
        )

        val tomorrow = now.plusDays(1)
        val london: TimeZone = TimeZone.getTimeZone("Europe/London")
        val instantInUK = tomorrow.toInstant.atZone(london.toZoneId)

        val expected = instantInUK.toLocalDate.atStartOfDay().atZone(london.toZoneId).toOffsetDateTime

        getNextMidnight(now) shouldBe expected
      }

      "return midnight of a day during BST" in {

        val now = OffsetDateTime.of(
          LocalDateTime.of(2019, 6, 14, 14, 30),
          BST_OFFSET
        )

        val tomorrow = now.plusDays(1)
        val london: TimeZone = TimeZone.getTimeZone("Europe/London")
        val instantInUK = tomorrow.toInstant.atZone(london.toZoneId)

        val expected = instantInUK.toLocalDate.atStartOfDay().plusHours(1).atZone(london.toZoneId).toOffsetDateTime

        getNextMidnight(now) shouldBe expected
      }
    }

    "the private function is called with a parameter" when {
      "the current timezone is GMT" should {
        "return the next UK midnight for that day" when {
          "the time is 1 nanosecond before midnight" in {
            val testDateTime = LocalDateTime.of(2018, 12, 31, 23, 59, 59, MAX_NANO_SECONDS)
            val offset = UK_TIME_ZONE.getRules.getOffset(testDateTime)
            val testOffsetDateTime = testDateTime.atOffset(offset)

            offset shouldBe GMT_OFFSET

            val uKNewYear = getNextMidnight(testOffsetDateTime)

            uKNewYear.toLocalDateTime shouldBe LocalDateTime.of(2019, 1, 1, 0, 0, 0, 0)
            uKNewYear.getOffset shouldBe offset
          }
          "the time is exactly midnight" in {
            val testDateTime = LocalDateTime.of(2018, 12, 31, 0, 0, 0, 0)
            val offset = UK_TIME_ZONE.getRules.getOffset(testDateTime)
            val testOffsetDateTime = testDateTime.atOffset(offset)

            offset shouldBe GMT_OFFSET

            val uKNewYear = getNextMidnight(testOffsetDateTime)

            uKNewYear.toLocalDateTime shouldBe LocalDateTime.of(2019, 1, 1, 0, 0, 0, 0)
            uKNewYear.getOffset shouldBe offset
          }
        }
        "the time is exactly 1 am and daylight saving (BST) starts at 1 am the next day" in {
          val testDateTime = LocalDateTime.of(2019, 3, 30, 1, 0, 0, 0)
          val offset = UK_TIME_ZONE.getRules.getOffset(testDateTime)
          val testOffsetDateTime = testDateTime.atOffset(offset)

          offset shouldBe GMT_OFFSET
          testDateTime.plusDays(1).atZone(UK_TIME_ZONE).getOffset shouldBe BST_OFFSET

          val anHourBeforeBst = getNextMidnight(testOffsetDateTime)

          anHourBeforeBst.toLocalDateTime shouldBe LocalDateTime.of(2019, 3, 31, 0, 0, 0, 0)
          anHourBeforeBst.getOffset shouldBe offset
        }
      }
      "the current timezone is BST" should {
        "return the UTC+0 midnight(1 am BST) for the next day" when {
          "the time is 1 nanosecond before BST midnight" in {
            val testDateTime = LocalDateTime.of(2019, 4, 1, 23, 59, 59, MAX_NANO_SECONDS)
            val offset = UK_TIME_ZONE.getRules.getOffset(testDateTime)
            val testOffsetDateTime = testDateTime.atOffset(offset)

            offset shouldBe BST_OFFSET

            val result = getNextMidnight(testOffsetDateTime)

            result.toLocalDateTime shouldBe LocalDateTime.of(2019, 4, 2, 1, 0, 0, 0)
            result.getOffset shouldBe offset
          }
          "the time is exactly 2 am and daylight saving (BST) ends at 2 am the next day" in {
            val testDateTime = LocalDateTime.of(2018, 10, 27, 2, 0, 0, 0)
            val offset = UK_TIME_ZONE.getRules.getOffset(testDateTime)
            val testOffsetDateTime = testDateTime.atOffset(offset)

            offset shouldBe BST_OFFSET
            testDateTime.plusDays(1).atZone(UK_TIME_ZONE).getOffset shouldBe GMT_OFFSET

            LocalDateTime.of(2018, 10, 28, 2, 0, 0, 0).atZone(UK_TIME_ZONE).getOffset shouldBe ZoneOffset.ofHours(0)

            val twoHoursBeforeBstEnds = getNextMidnight(testOffsetDateTime)

            twoHoursBeforeBstEnds.toLocalDateTime shouldBe LocalDateTime.of(2018, 10, 28, 1, 0, 0, 0)
            twoHoursBeforeBstEnds.getOffset shouldBe offset
          }
        }
      }
      "have safe guard behaviour in daylight saving (BST) since we are not certain what time zone NPS is using" when {
        "the user enters just before midnight BST" should {
          "return the next 1 am BST as normal" in {
            val testDateTime = LocalDateTime.of(2019, 4, 1, 23, 59, 59, MAX_NANO_SECONDS)
            val offset = UK_TIME_ZONE.getRules.getOffset(testDateTime)
            val testOffsetDateTime = testDateTime.atOffset(offset)

            offset shouldBe BST_OFFSET

            val result = getNextMidnight(testOffsetDateTime)

            result.toLocalDateTime shouldBe LocalDateTime.of(2019, 4, 2, 1, 0, 0, 0)
            result.getOffset shouldBe offset
          }
        }
        "the user enters at midnight BST" should {
          "return midnight BST of the following day instead" in {
            val testDateTime = LocalDateTime.of(2019, 4, 2, 0, 0, 0, 0)
            val offset = UK_TIME_ZONE.getRules.getOffset(testDateTime)
            val testOffsetDateTime = testDateTime.atOffset(offset)

            offset shouldBe BST_OFFSET

            val result = getNextMidnight(testOffsetDateTime)

            result.toLocalDateTime shouldBe LocalDateTime.of(2019, 4, 3, 0, 0, 0, 0)
            result.getOffset shouldBe offset
          }
        }
        "the user enters just before 1 am BST" should {
          "return midnight BST of the following day instead" in {
            val testDateTime = LocalDateTime.of(2019, 4, 2, 0, 59, 59, MAX_NANO_SECONDS)
            val offset = UK_TIME_ZONE.getRules.getOffset(testDateTime)
            val testOffsetDateTime = testDateTime.atOffset(offset)

            offset shouldBe BST_OFFSET

            val result = getNextMidnight(testOffsetDateTime)

            result.toLocalDateTime shouldBe LocalDateTime.of(2019, 4, 3, 0, 0, 0, 0)
            result.getOffset shouldBe offset
          }
        }
        "the user enters at 1 am BST" should {
          "return the next 1 am BST as normal" in {
            val testDateTime = LocalDateTime.of(2019, 4, 2, 1, 0, 0, 0)
            val offset = UK_TIME_ZONE.getRules.getOffset(testDateTime)
            val testOffsetDateTime = testDateTime.atOffset(offset)

            offset shouldBe BST_OFFSET

            val result = getNextMidnight(testOffsetDateTime)

            result.toLocalDateTime shouldBe LocalDateTime.of(2019, 4, 3, 1, 0, 0, 0)
            result.getOffset shouldBe offset
          }
        }
      }
    }
  }

  "isLockPresent" when {

    "where no nino in request" should {

      "return false" in {
        def userRequest[A]: UserRequest[A] =
          buildUserRequest(nino = None, request = FakeRequest().asInstanceOf[Request[A]])

        val locks = List(correspondenceLock)
        await(sut(locks).isLockPresent(PostalAddrType)(userRequest)) shouldBe false
      }
    }

    "a correspondence address" should {
      "return true if correspondence lock is present" in {
        val locks = List(correspondenceLock)
        await(sut(locks).isLockPresent(PostalAddrType)(userRequest)) shouldBe true
      }
      "return false if no locks are present" in {
        val locks = List()
        await(sut(locks).isLockPresent(PostalAddrType)(userRequest)) shouldBe false
      }
      "return false if sole lock is present" in {
        val locks = List(soleLock)
        await(sut(locks).isLockPresent(PostalAddrType)(userRequest)) shouldBe false
      }
      "return true if primary lock is present" in {
        val locks = List(primaryLock)
        await(sut(locks).isLockPresent(PostalAddrType)(userRequest)) shouldBe false
      }
    }

    "a sole address" should {
      "return true if sole lock is present" in {
        val locks = List(soleLock)
        await(sut(locks).isLockPresent(SoleAddrType)(userRequest)) shouldBe true
      }
      "return false if no locks are present" in {
        val locks = List()
        await(sut(locks).isLockPresent(SoleAddrType)(userRequest)) shouldBe false
      }
      "return false if correspondence lock is present" in {
        val locks = List(correspondenceLock)
        await(sut(locks).isLockPresent(SoleAddrType)(userRequest)) shouldBe false
      }
      "return true if primary lock is present" in {
        val locks = List(primaryLock)
        await(sut(locks).isLockPresent(SoleAddrType)(userRequest)) shouldBe true
      }
    }

    "a primary address" should {
      "return true if primary lock is present" in {
        val locks = List(primaryLock)
        await(sut(locks).isLockPresent(PrimaryAddrType)(userRequest)) shouldBe true
      }
      "return false if no locks are present" in {
        val locks = List()
        await(sut(locks).isLockPresent(PrimaryAddrType)(userRequest)) shouldBe false
      }
      "return false if correspondence lock is present" in {
        val locks = List(correspondenceLock)
        await(sut(locks).isLockPresent(PrimaryAddrType)(userRequest)) shouldBe false
      }
      "return true if sole lock is present" in {
        val locks = List(soleLock)
        await(sut(locks).isLockPresent(PrimaryAddrType)(userRequest)) shouldBe true
      }
    }

    "a generic main address" should {
      "return true if primary lock is present" in {
        val locks = List(primaryLock)
        await(sut(locks).isLockPresent(MainAddrType)(userRequest)) shouldBe true
      }
      "return false if no locks are present" in {
        val locks = List()
        await(sut(locks).isLockPresent(MainAddrType)(userRequest)) shouldBe false
      }
      "return false if correspondence lock is present" in {
        val locks = List(correspondenceLock)
        await(sut(locks).isLockPresent(MainAddrType)(userRequest)) shouldBe false
      }
      "return true if sole lock is present" in {
        val locks = List(soleLock)
        await(sut(locks).isLockPresent(MainAddrType)(userRequest)) shouldBe true
      }
    }
  }

}
