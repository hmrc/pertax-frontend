/*
 * Copyright 2023 HM Revenue & Customs
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
import connectors.{EnrolmentsConnector, UsersGroupsSearchConnector}
import models._
import models.enrolments.{EACDEnrolment, IdentifiersOrVerifiers, KnownFactResponseForNINO}
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json
import testUtils.BaseSpec
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.domain.{Generator, Nino}

import scala.concurrent.Future

class EnrolmentStoreCachingServiceSpec extends BaseSpec {

  val mockSessionCache: LocalSessionCache                        = mock[LocalSessionCache]
  val mockEnrolmentsConnector: EnrolmentsConnector               = mock[EnrolmentsConnector]
  val mockUsersGroupsSearchConnector: UsersGroupsSearchConnector = mock[UsersGroupsSearchConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSessionCache, mockEnrolmentsConnector, mockUsersGroupsSearchConnector)
  }

  trait LocalSetup {

    val cacheResult: CacheMap                                       = CacheMap("", Map.empty)
    val fetchResult: Option[SelfAssessmentUserType]                 = None
    val connectorResult: Either[UpstreamErrorResponse, Seq[String]] = Right(Seq[String]())

    lazy val sut: EnrolmentStoreCachingService = {

      val c =
        new EnrolmentStoreCachingService(mockSessionCache, mockEnrolmentsConnector, mockUsersGroupsSearchConnector)

      when(
        mockSessionCache.cache[SelfAssessmentUserType](any(), any())(any(), any(), any())
      ).thenReturn(Future.successful(cacheResult))

      when(
        mockSessionCache.fetchAndGetEntry[SelfAssessmentUserType](any())(any(), any(), any())
      ).thenReturn(Future.successful(fetchResult))

      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Seq[String]](
          Future.successful(
            connectorResult
          )
        )
      )

      c
    }
  }

  val saUtr: SaUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  "EnrolmentStoreCachingService" when {

    "the cache is empty and the connector is called" must {

      "return NonFilerSelfAssessmentUser when the connector returns a Left" in new LocalSetup {

        override val connectorResult: Either[UpstreamErrorResponse, Seq[String]] =
          Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR))

        sut.getSaUserTypeFromCache(saUtr).futureValue mustBe NonFilerSelfAssessmentUser
      }

      "return NotEnrolledSelfAssessmentUser when the connector returns a Right with an empty sequence" in new LocalSetup {

        sut.getSaUserTypeFromCache(saUtr).futureValue mustBe NotEnrolledSelfAssessmentUser(saUtr)
      }

      "return WrongCredentialsSelfAssessmentUser when the connector returns a Right with a non-empty sequence" in new LocalSetup {

        override val connectorResult: Either[UpstreamErrorResponse, Seq[String]] = Right(Seq[String]("Hello there"))

        sut.getSaUserTypeFromCache(saUtr).futureValue mustBe WrongCredentialsSelfAssessmentUser(saUtr)
      }
    }

    "only call the connector once" in {
      lazy val sut: EnrolmentStoreCachingService =
        new EnrolmentStoreCachingService(mockSessionCache, mockEnrolmentsConnector, mockUsersGroupsSearchConnector)

      val cacheMap =
        CacheMap("id", Map("id" -> Json.toJson(NotEnrolledSelfAssessmentUser(saUtr): SelfAssessmentUserType)))

      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Seq[String]](
          Future.successful(
            Right(Seq[String]())
          )
        )
      )

      when(mockSessionCache.fetchAndGetEntry[SelfAssessmentUserType](any())(any(), any(), any())).thenReturn(
        Future
          .successful(None),
        Future.successful(Some(NotEnrolledSelfAssessmentUser(saUtr)))
      )

      when(mockSessionCache.cache[SelfAssessmentUserType](any(), any())(any(), any(), any())).thenReturn(
        Future
          .successful(cacheMap)
      )

      sut.getSaUserTypeFromCache(saUtr).futureValue

      sut.getSaUserTypeFromCache(saUtr).futureValue

      verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any(), any())(any(), any())
    }

    "retrieveMTDEnrolment" must {
      "return MTDIT value" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(mockSessionCache, mockEnrolmentsConnector, mockUsersGroupsSearchConnector)

        lazy val enrolment      = KnownFactResponseForNINO(
          "IR-SA",
          List(EACDEnrolment(List.empty, List(IdentifiersOrVerifiers("MTDITID", "Enrolment Value"))))
        )
        lazy val testNino: Nino = new Generator().nextNino

        when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[KnownFactResponseForNINO]](
            Future.successful(
              Right(Some(enrolment))
            )
          )
        )

        val result = sut.retrieveMTDEnrolment(testNino)
        verify(mockEnrolmentsConnector, times(1)).getKnownFacts(any())(any(), any())
        result.futureValue mustBe Some("Enrolment Value")
      }
      "return None when no verifiers are returned" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(mockSessionCache, mockEnrolmentsConnector, mockUsersGroupsSearchConnector)

        lazy val enrolment      = KnownFactResponseForNINO(
          "IR-SA",
          List(EACDEnrolment(List.empty, List.empty))
        )
        lazy val testNino: Nino = new Generator().nextNino

        when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[KnownFactResponseForNINO]](
            Future.successful(
              Right(Some(enrolment))
            )
          )
        )

        val result = sut.retrieveMTDEnrolment(testNino)
        verify(mockEnrolmentsConnector, times(1)).getKnownFacts(any())(any(), any())
        result.futureValue mustBe None
      }

      "return None when connector call fails" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(mockSessionCache, mockEnrolmentsConnector, mockUsersGroupsSearchConnector)

        lazy val testNino: Nino = new Generator().nextNino

        when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[KnownFactResponseForNINO]](
            Future.successful(
              Left(UpstreamErrorResponse.apply("ERROR", 400))
            )
          )
        )

        val result = sut.retrieveMTDEnrolment(testNino)
        verify(mockEnrolmentsConnector, times(1)).getKnownFacts(any())(any(), any())
        result.futureValue mustBe None
      }
    }
    "checkEnrolmentId"     must {
      "return the head primaryId when found" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(mockSessionCache, mockEnrolmentsConnector, mockUsersGroupsSearchConnector)

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(
              Right(Seq("ID 1", "ID 2", "ID 3"))
            )
          )
        )

        val result = sut.checkEnrolmentId("KEY", "VALUE")
        result.futureValue mustBe Some("ID 1")
      }
      "return none when no enrolments returned" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(mockSessionCache, mockEnrolmentsConnector, mockUsersGroupsSearchConnector)

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(
              Right(Seq.empty)
            )
          )
        )

        val result = sut.checkEnrolmentId("KEY", "VALUE")
        result.futureValue mustBe None
      }

      "return none when an upstream error occurs" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(mockSessionCache, mockEnrolmentsConnector, mockUsersGroupsSearchConnector)

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(
              Left(UpstreamErrorResponse.apply("ERROR", 400))
            )
          )
        )

        val result = sut.checkEnrolmentId("KEY", "VALUE")
        result.futureValue mustBe None
      }
    }
  }
}
