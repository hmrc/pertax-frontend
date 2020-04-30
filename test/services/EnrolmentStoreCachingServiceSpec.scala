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

package services

import connectors.EnrolmentsConnector
import models._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.cache.client.CacheMap
import util.BaseSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrolmentStoreCachingServiceSpec extends BaseSpec with MockitoSugar with ScalaFutures {

  trait LocalSetup {

    val mockSessionCache: LocalSessionCache = mock[LocalSessionCache]
    val mockEnrolmentsConnector: EnrolmentsConnector = mock[EnrolmentsConnector]

    val cacheResult: CacheMap = CacheMap("", Map.empty)
    val fetchResult: Option[SelfAssessmentUserType] = None
    val connectorResult: Either[String, Seq[String]] = Right(Seq[String]())

    lazy val sut: EnrolmentStoreCachingService = {

      val c = new EnrolmentStoreCachingService(mockSessionCache, mockEnrolmentsConnector)

      when(
        mockSessionCache.cache[SelfAssessmentUserType](any(), any())(any(), any(), any())
      ) thenReturn Future.successful(cacheResult)

      when(
        mockSessionCache.fetchAndGetEntry[SelfAssessmentUserType](any())(any(), any(), any())
      ) thenReturn Future.successful(fetchResult)

      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())) thenReturn Future.successful(
        connectorResult)

      c
    }
  }

  val saUtr = SaUtr("111111111")

  "EnrolmentStoreCachingService" when {

    "the cache is empty and the connector is called" should {

      "return NonFilerSelfAssessmentUser when the connector returns a Left" in new LocalSetup {

        override val connectorResult: Either[String, Seq[String]] = Left("An error has occurred")

        sut.getSaUserTypeFromCache(saUtr).futureValue shouldBe NonFilerSelfAssessmentUser
      }

      "return NotEnrolledSelfAssessmentUser when the connector returns a Right with an empty sequence" in new LocalSetup {

        sut.getSaUserTypeFromCache(saUtr).futureValue shouldBe NotEnrolledSelfAssessmentUser(saUtr)
      }

      "return WrongCredentialsSelfAssessmentUser when the connector returns a Right with a non-empty sequence" in new LocalSetup {

        override val connectorResult: Either[String, Seq[String]] = Right(Seq[String]("Hello there"))

        sut.getSaUserTypeFromCache(saUtr).futureValue shouldBe WrongCredentialsSelfAssessmentUser(saUtr)
      }
    }
  }
}
