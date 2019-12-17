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

import connectors.EnrolmentsConnector
import controllers.bindable.{PostalAddrType, SoleAddrType}
import helpers.IntegrationHelpers
import models.{AddressJourneyTTLModel, EditCorrespondenceAddress, EditSoleAddress}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.mockito.MockitoSugar.mock
import org.mockito.Mockito._
import org.mockito.Matchers.any
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import services.{EnrolmentStoreCachingService, LocalSessionCache}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class CachingItSpec extends IntegrationHelpers
  with PatienceConfiguration
  with BeforeAndAfterEach {

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  def mongo: EditAddressLockRepository = app.injector.instanceOf[EditAddressLockRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(cache.remove())
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

  def editedAddress(dateTime: OffsetDateTime) = EditSoleAddress(BSONDateTime(dateTime.toInstant.toEpochMilli))
  def editedOtherAddress(dateTime: OffsetDateTime) = EditCorrespondenceAddress(BSONDateTime(dateTime.toInstant.toEpochMilli))

  "editAddressLockRepository" when {

    "setIndex is called" should {
      "ensure the index is set" in {
        await(mongo.removeIndex())
        await(mongo.isTtlSet) shouldBe false

        await(mongo.setIndex())
        await(mongo.isTtlSet) shouldBe true
      }
    }

    "get is called" should {
      "return an empty list" when {
        "there isn't an existing record" in {
          val fGet = mongo.get(testNino.withoutSuffix)

          await(fGet) shouldBe List.empty
        }

        "there isn't an existing record that matches the requested nino" in {
          val timeOffSet = 10L

          await(mongo.insertCore(AddressJourneyTTLModel(testNino.withoutSuffix, editedAddress(OffsetDateTime.now().plusSeconds(timeOffSet)))))

          val fGet = mongo.get(differentNino.withoutSuffix)

          await(fGet) shouldBe List.empty

        }

        "there is an existing record but has expired" in {

          val nino = testNino.withoutSuffix

          await(mongo.insertCore(AddressJourneyTTLModel(nino, editedAddress(OffsetDateTime.now()))))

          val fGet = mongo.get(nino)

          await(fGet) shouldBe List.empty
        }
      }

      "return the record" when {
        "there is an existing record and it has not yet expired" in {
          val currentTime = System.currentTimeMillis()
          val timeOffSet = 10L

          val nino = testNino.withoutSuffix

          await(mongo.insertCore(AddressJourneyTTLModel(nino, editedAddress(OffsetDateTime.now().plusSeconds(timeOffSet)))))

          val fGet = mongo.get(nino)
          val inserted = await(mongo.getCore(BSONDocument()))
          currentTime should be < inserted.head.editedAddress.expireAt.value
          await(fGet) shouldBe inserted
        }
      }

      "return multiple records" when {
        "multiple existing records are present" in {
          val timeOffSet = 10L

          val nino = testNino.withoutSuffix

          val address1 = AddressJourneyTTLModel(nino, editedAddress(OffsetDateTime.now().plusSeconds(timeOffSet)))
          val address2 = AddressJourneyTTLModel(nino, editedOtherAddress(OffsetDateTime.now().plusSeconds(200)))

          await(mongo.insertCore(address1))
          await(mongo.insertCore(address2))

          val result = await(mongo.get(nino))

          result shouldBe List(address1, address2)
        }
      }
    }

    "insert is called" should {
      "return true" when {
        "there isn't an existing record" in {
          import EditAddressLockRepository._
          val offsetTime = getNextMidnight(OffsetDateTime.now())
          val midnight = toBSONDateTime(offsetTime)

          val nino = testNino.withoutSuffix

          val result = await(mongo.insert(nino, SoleAddrType))
          result shouldBe true

          val inserted = await(mongo.get(nino))

          inserted.head.nino shouldBe nino
          inserted.head.editedAddress.expireAt shouldBe midnight
        }
      }

      "return false" when {
        "there is an existing record" in {

          val nino = testNino.withoutSuffix

          await(mongo.insert(nino, PostalAddrType))

          val result = await(mongo.insert(nino, PostalAddrType))

          result shouldBe false
        }
      }
    }
  }

  val cache: LocalSessionCache = app.injector.instanceOf[LocalSessionCache]
  val mockConnector: EnrolmentsConnector = mock[EnrolmentsConnector]

  val service = new EnrolmentStoreCachingService(cache, mockConnector)

  val saUtr = SaUtr("789456123")

  "EnrolmentStoreCachingService" when {

    "getSaUserTypeFromCache is called" should {

      "only call the connector once" in {

        when(mockConnector.getUserIdsWithEnrolments(any())(any(), any())
        ) thenReturn Future.successful(Right(Seq[String]()))

        await(service.getSaUserTypeFromCache(saUtr))

        await(service.getSaUserTypeFromCache(saUtr))

        verify(mockConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
      }
    }
  }
}
