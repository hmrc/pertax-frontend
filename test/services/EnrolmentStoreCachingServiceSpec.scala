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

package services

import cats.data.EitherT
import connectors.EnrolmentsConnector
import models._
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.INTERNAL_SERVER_ERROR
import repositories.JourneyCacheRepository
import routePages.SelfAssessmentUserTypePage
import testUtils.BaseSpec
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class EnrolmentStoreCachingServiceSpec extends BaseSpec {

  val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]
  val mockEnrolmentsConnector: EnrolmentsConnector       = mock[EnrolmentsConnector]
  val saUtr: SaUtr                                       = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJourneyCacheRepository, mockEnrolmentsConnector)
  }

  trait LocalSetup {

    when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
    when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(UserAnswers.empty("")))

    lazy val sut: EnrolmentStoreCachingService =
      new EnrolmentStoreCachingService(mockJourneyCacheRepository, mockEnrolmentsConnector)
  }

  "EnrolmentStoreCachingService" when {

    "the cache is empty and the connector is called" must {

      "return NonFilerSelfAssessmentUser when the connector returns a Left" in new LocalSetup {

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
          )
        )

        val result: SelfAssessmentUserType = sut.getSaUserTypeFromCache(saUtr).futureValue

        result mustBe NonFilerSelfAssessmentUser

        verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
      }

      "return NotEnrolledSelfAssessmentUser when the connector returns a Right with an empty sequence" in new LocalSetup {

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(Right(Seq[String]()))
          )
        )

        val result: SelfAssessmentUserType = sut.getSaUserTypeFromCache(saUtr).futureValue

        result mustBe NotEnrolledSelfAssessmentUser(saUtr)

        verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
      }

      "return WrongCredentialsSelfAssessmentUser when the connector returns a Right with a non-empty sequence" in new LocalSetup {

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(Right(Seq("Hello there")))
          )
        )

        val result: SelfAssessmentUserType = sut.getSaUserTypeFromCache(saUtr).futureValue

        result mustBe WrongCredentialsSelfAssessmentUser(saUtr)

        verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
      }
    }

    "only call the connector once" in {

      val cachedUserAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelfAssessmentUserTypePage, NotEnrolledSelfAssessmentUser(saUtr))

      val sut = new EnrolmentStoreCachingService(mockJourneyCacheRepository, mockEnrolmentsConnector)

      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any())(any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Seq[String]](
          Future.successful(
            Right(Seq[String]())
          )
        )
      )

      when(mockJourneyCacheRepository.get(any())).thenReturn(
        Future.successful(UserAnswers.empty("id")),
        Future.successful(cachedUserAnswers)
      )

      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      sut.getSaUserTypeFromCache(saUtr).futureValue

      sut.getSaUserTypeFromCache(saUtr).futureValue

      verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
    }
  }
}
