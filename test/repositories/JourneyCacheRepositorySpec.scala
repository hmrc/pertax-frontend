/*
 * Copyright 2024 HM Revenue & Customs
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
import controllers.bindable.PostalAddrType
import models.UserAnswers
import models.dto.AddressDto
import org.mockito.Mockito.when
import org.mongodb.scala.model.Filters
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import routePages.SubmittedAddressPage
import play.api.libs.json.Json
import testUtils.Fixtures.fakeStreetTupleListAddressForUnmodified
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global

class JourneyCacheRepositorySpec
    extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UserAnswers]
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with MockitoSugar {

  private val instant          = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

  private val id             = "id"
  val addressDto: AddressDto = asAddressDto(fakeStreetTupleListAddressForUnmodified)
  private val userAnswers    = UserAnswers(id, Json.obj(), Instant.ofEpochSecond(1))
    .setOrException(SubmittedAddressPage(PostalAddrType), addressDto)

  private implicit val hc: HeaderCarrier = HeaderCarrier.apply(sessionId = Some(SessionId("id")))

  private val idNotExisting = "id that does not exist"

  private val mockConfigDecorator = mock[ConfigDecorator]
  when(mockConfigDecorator.sessionTimeoutInSeconds) thenReturn 1

  protected override val repository: JourneyCacheRepository = new JourneyCacheRepository(
    mongoComponent = mongoComponent,
    appConfig = mockConfigDecorator,
    clock = stubClock
  )

  ".set" - {
    "must set the last updated time on the supplied user answers to `now`, and save them" in {
      val expectedResult = userAnswers copy (lastUpdated = instant)
      repository.set(userAnswers).futureValue
      val updatedRecord  = find(Filters.equal("_id", userAnswers.id)).futureValue.headOption.value
      updatedRecord mustEqual expectedResult
    }
  }

  ".get" - {
    "when there is a record for this id" - {
      "must update the lastUpdated time and get the record" in {
        insert(userAnswers).futureValue
        val result         = repository.get.futureValue
        val expectedResult = userAnswers copy (lastUpdated = instant)
        result mustEqual expectedResult
      }
    }

    "when there is no record for this id" - {
      "must return new user answers with no user and session id" in {
        val hc: HeaderCarrier = HeaderCarrier.apply(sessionId = Some(SessionId(idNotExisting)))
        val res               = repository.get(hc).futureValue
        res.get(SubmittedAddressPage(PostalAddrType)) mustBe None
        res.id mustBe idNotExisting
      }
    }
  }

  ".clear" - {
    "must remove record for current session" in {
      insert(userAnswers).futureValue
      repository.clear.futureValue mustEqual (): Unit
      val res: UserAnswers = repository.get.futureValue
      res.get(SubmittedAddressPage(PostalAddrType)) mustBe None
    }
    "must successfully when there is no record to remove" in {
      val hc: HeaderCarrier = HeaderCarrier.apply(sessionId = Some(SessionId(idNotExisting)))
      repository.clear(hc).futureValue mustBe (): Unit
    }
  }

  ".keepAlive" - {

    "when there is a record for this id" - {
      "must update its lastUpdated to `now`" in {
        insert(userAnswers).futureValue
        repository.keepAlive.futureValue

        val expectedUpdatedAnswers = userAnswers copy (lastUpdated = instant)
        val updatedAnswers         = find(Filters.equal("_id", userAnswers.id)).futureValue.headOption.value
        updatedAnswers mustEqual expectedUpdatedAnswers
      }
    }

    "when there is no record for this id" - {
      "must return unit + no exception" in {
        val hc: HeaderCarrier = HeaderCarrier.apply(sessionId = Some(SessionId(idNotExisting)))
        repository.keepAlive(hc).futureValue mustEqual (): Unit
      }
    }
  }
}
