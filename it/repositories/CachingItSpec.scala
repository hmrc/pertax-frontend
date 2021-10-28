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

import config.ConfigDecorator
import connectors.EnrolmentsConnector
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import models.{AddressJourneyTTLModel, EditCorrespondenceAddress, EditSoleAddress}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import services.{EnrolmentStoreCachingService, LocalSessionCache}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import scala.concurrent.Future
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

class CachingItSpec extends AnyWordSpecLike with Matchers
  with DefaultPlayMongoRepositorySupport[AddressJourneyTTLModel]
  with PatienceConfiguration {

  //implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]


 // def mongo: EditAddressLockRepository = app.injector.instanceOf[EditAddressLockRepository]

//  override def beforeEach(): Unit = {
//    super.beforeEach()
//    //await(cache.remove())
//   // await(mongo.drop)
//  }

  lazy val config = mock[ConfigDecorator]

  private lazy val ttl = 10

  when(config.editAddressTtl).thenReturn(ttl)

  override lazy val repository = new EditAddressLockRepository(
    config,
    mongoComponent
  )

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

  def editedAddress() = EditSoleAddress(Instant.now().minusSeconds(20))
  def editedOtherAddress() = EditCorrespondenceAddress(Instant.now())

  "editAddressLockRepository" when {

    "get is called" should {
      "return an empty list" when {
        "there isn't an existing record" in {
          val fGet = repository.get(testNino.withoutSuffix)

          await(fGet) shouldBe List.empty
        }

        "there isn't an existing record that matches the requested nino" in {
          val timeOffSet = 10L

          await(repository.insertCore(AddressJourneyTTLModel(testNino.withoutSuffix, editedAddress())))

          val fGet = repository.get(differentNino.withoutSuffix)

          await(fGet) shouldBe List.empty

        }

        "there is an existing record but has expired" in {

          val nino = testNino.withoutSuffix

          await(repository.insertCore(AddressJourneyTTLModel(nino, editedAddress())))

          Thread.sleep(60000)

          val fGet = repository.get(nino)

          await(fGet) shouldBe List.empty
        }
      }

      "return multiple records" when {
        "multiple existing records are present" in {

          val nino = testNino.withoutSuffix

          val address1 = AddressJourneyTTLModel(nino, editedAddress())
          val address2 = AddressJourneyTTLModel(nino, editedOtherAddress())

          await(repository.insertCore(address1))
          await(repository.insertCore(address2))

          val result = await(repository.get(nino))

          result should contain theSameElementsAs List(address2, address1)
        }
      }
    }

    "insert is called" should {
      "return true" when {
        "there isn't an existing record" in {
          import EditAddressLockRepository._
          val offsetTime = getNextMidnight(OffsetDateTime.now())

          val midnight = offsetTime.toInstant.toEpochMilli

          val nino = testNino.withoutSuffix

          val result = await(repository.insert(nino, ResidentialAddrType))
          result shouldBe true

          val inserted = await(repository.get(nino))

          inserted.head.nino shouldBe nino
          inserted.head.editedAddress.expireAt.toEpochMilli shouldBe midnight
        }
      }

      "return false" when {
        "there is an existing record" in {

          val nino = testNino.withoutSuffix

          await(repository.insert(nino, PostalAddrType))

          val result = await(repository.insert(nino, PostalAddrType))

          result shouldBe false
        }
      }
    }
  }


}
