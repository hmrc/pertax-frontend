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

import connectors.EnrolmentsConnector
import controllers.bindable.{PostalAddrType, SoleAddrType}
import models.{AddressJourneyTTLModel, EditCorrespondenceAddress, EditSoleAddress}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.enablers.Aggregating
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import services.{EnrolmentStoreCachingService, LocalSessionCache}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}

import java.time.OffsetDateTime
import java.util.UUID
import scala.collection.GenTraversable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class CachingItSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with ScalaFutures
  with PatienceConfiguration
  with BeforeAndAfterEach {

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val hc = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID()}")))

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

    "setIndex is called" must {
      "ensure the index is set" in {
        mongo.removeIndex().futureValue
        mongo.isTtlSet.futureValue mustBe false

        mongo.setIndex().futureValue
        mongo.isTtlSet.futureValue mustBe true
      }
    }

    "get is called" when {
      "return an empty list" must {
        "there isn't an existing record" in {
          val fGet = mongo.get(testNino.withoutSuffix)

          await(fGet) mustBe List.empty
        }

        "there isn't an existing record that matches the requested nino" in {
          val timeOffSet = 10L

          mongo.insertCore(AddressJourneyTTLModel(testNino.withoutSuffix, editedAddress(OffsetDateTime.now().plusSeconds(timeOffSet))))

          val fGet = mongo.get(differentNino.withoutSuffix)

          await(fGet) mustBe List.empty

        }

        "there is an existing record but has expired" in {

          val nino = testNino.withoutSuffix

          mongo.insertCore(AddressJourneyTTLModel(nino, editedAddress(OffsetDateTime.now())))

          val fGet = mongo.get(nino)

          await(fGet) mustBe List.empty
        }
      }

      "return the record" must {
        "there is an existing record and it has not yet expired" in {
          val currentTime = System.currentTimeMillis()
          val timeOffSet = 10L

          val nino = testNino.withoutSuffix

          mongo.insertCore(AddressJourneyTTLModel(nino, editedAddress(OffsetDateTime.now().plusSeconds(timeOffSet)))).futureValue

          val fGet = mongo.get(nino).futureValue
          val inserted = mongo.getCore(BSONDocument()).futureValue
          currentTime must be < inserted.head.editedAddress.expireAt.value
          fGet mustBe inserted
        }
      }

      "return multiple records" must {
        "multiple existing records are present" in {
          val timeOffSet = 10L

          val nino = testNino.withoutSuffix

          val address1 = AddressJourneyTTLModel(nino, editedAddress(OffsetDateTime.now().plusSeconds(timeOffSet)))
          val address2 = AddressJourneyTTLModel(nino, editedOtherAddress(OffsetDateTime.now().plusSeconds(200)))

          class Aggregate extends Aggregating[Future[List[AddressJourneyTTLModel]]] {
            override def containsAtLeastOneOf(aggregation: Future[List[AddressJourneyTTLModel]], eles: Seq[Any]): Boolean = false

            override def containsTheSameElementsAs(leftAggregation: Future[List[AddressJourneyTTLModel]], rightAggregation: GenTraversable[Any]): Boolean = true

            override def containsOnly(aggregation: Future[List[AddressJourneyTTLModel]], eles: Seq[Any]): Boolean = false

            override def containsAllOf(aggregation: Future[List[AddressJourneyTTLModel]], eles: Seq[Any]): Boolean = false

            override def containsAtMostOneOf(aggregation: Future[List[AddressJourneyTTLModel]], eles: Seq[Any]): Boolean = false
          }

          implicit lazy val aggregate: Aggregate = new Aggregate

          mongo.insertCore(address1)
          mongo.insertCore(address2)

          val result = mongo.get(nino)

          result must contain theSameElementsAs List(address2, address1)
        }
      }
    }

    "insert is called" when {
      "return true" must {
        "there isn't an existing record" in {
          import EditAddressLockRepository._
          val offsetTime = getNextMidnight(OffsetDateTime.now())
          val midnight = toBSONDateTime(offsetTime)

          val nino = testNino.withoutSuffix

          val result = mongo.insert(nino, SoleAddrType).futureValue
          result mustBe true

          val inserted = mongo.get(nino)

          inserted.futureValue.head.nino mustBe nino
          inserted.futureValue.head.editedAddress.expireAt mustBe midnight
        }
      }

      "return false" must {
        "there is an existing record" in {

          val nino = testNino.withoutSuffix

          mongo.insert(nino, PostalAddrType)

          val result = mongo.insert(nino, PostalAddrType).futureValue

          result mustBe false
        }
      }
    }
  }

  val cache: LocalSessionCache = app.injector.instanceOf[LocalSessionCache]
  val mockConnector: EnrolmentsConnector = mock[EnrolmentsConnector]

  val service = new EnrolmentStoreCachingService(cache, mockConnector)

  val saUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  "EnrolmentStoreCachingService" when {

    "getSaUserTypeFromCache is called" must {

      "only call the connector once" in {

        when(mockConnector.getUserIdsWithEnrolments(any())(any(), any())
        ) thenReturn Future.successful(Right(Seq[String]()))

        service.getSaUserTypeFromCache(saUtr).futureValue

        service.getSaUserTypeFromCache(saUtr).futureValue

        verify(mockConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
      }
    }
  }
}
