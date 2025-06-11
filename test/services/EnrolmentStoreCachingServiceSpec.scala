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
import connectors.{EnrolmentsConnector, UsersGroupsSearchConnector}
import models._
import models.enrolments.{AccountDetails, AdditionalFactors, EACDEnrolment, EnrolmentDoesNotExist, EnrolmentError, IdentifiersOrVerifiers, KnownFactResponseForNINO, SCP, UsersAssignedEnrolment, UsersGroupResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import repositories.JourneyCacheRepository
import routePages.SelfAssessmentUserTypePage
import testUtils.BaseSpec
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.domain.{Generator, Nino}

import scala.concurrent.Future

class EnrolmentStoreCachingServiceSpec extends BaseSpec {

  val mockJourneyCacheRepository: JourneyCacheRepository         = mock[JourneyCacheRepository]
  val mockEnrolmentsConnector: EnrolmentsConnector               = mock[EnrolmentsConnector]
  val mockUsersGroupsSearchConnector: UsersGroupsSearchConnector = mock[UsersGroupsSearchConnector]
  val saUtr: SaUtr                                               = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJourneyCacheRepository)
    reset(mockEnrolmentsConnector)
    reset(mockUsersGroupsSearchConnector)
  }

  trait LocalSetup {

    when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
    when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(UserAnswers.empty("")))

    lazy val sut: EnrolmentStoreCachingService =
      new EnrolmentStoreCachingService(
        mockJourneyCacheRepository,
        mockEnrolmentsConnector,
        mockUsersGroupsSearchConnector
      )
  }

  "EnrolmentStoreCachingService" when {

    "the cache is empty and the connector is called" must {

      "return NonFilerSelfAssessmentUser when the connector returns a Left" in new LocalSetup {

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
          )
        )

        val result: SelfAssessmentUserType = sut.getSaUserTypeFromCache(saUtr).futureValue

        result mustBe NonFilerSelfAssessmentUser

        verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any(), any())(any(), any())
      }

      "return NotEnrolledSelfAssessmentUser when the connector returns a Right with an empty sequence" in new LocalSetup {

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(Right(Seq[String]()))
          )
        )

        val result: SelfAssessmentUserType = sut.getSaUserTypeFromCache(saUtr).futureValue

        result mustBe NotEnrolledSelfAssessmentUser(saUtr)

        verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any(), any())(any(), any())
      }

      "return WrongCredentialsSelfAssessmentUser when the connector returns a Right with a non-empty sequence" in new LocalSetup {

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(Right(Seq("Hello there")))
          )
        )

        val result: SelfAssessmentUserType = sut.getSaUserTypeFromCache(saUtr).futureValue

        result mustBe WrongCredentialsSelfAssessmentUser(saUtr)

        verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any(), any())(any(), any())
      }
    }

    "only call the connector once" in {

      val cachedUserAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelfAssessmentUserTypePage, NotEnrolledSelfAssessmentUser(saUtr))

      val sut = new EnrolmentStoreCachingService(
        mockJourneyCacheRepository,
        mockEnrolmentsConnector,
        mockUsersGroupsSearchConnector
      )

      when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Seq[String]](
          Future.successful(
            Right(Seq[String]())
          )
        )
      )

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(UserAnswers.empty("id")),
        Future.successful(cachedUserAnswers)
      )

      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      sut.getSaUserTypeFromCache(saUtr).futureValue

      sut.getSaUserTypeFromCache(saUtr).futureValue

      verify(mockEnrolmentsConnector, times(1)).getUserIdsWithEnrolments(any(), any())(any(), any())
    }

    "retrieveMTDEnrolment" must {
      "return MTDIT value" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

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
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

        lazy val testNino: Nino = new Generator().nextNino

        when(mockEnrolmentsConnector.getKnownFacts(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[KnownFactResponseForNINO]](
            Future.successful(
              Right(None)
            )
          )
        )

        val result = sut.retrieveMTDEnrolment(testNino)
        verify(mockEnrolmentsConnector, times(1)).getKnownFacts(any())(any(), any())
        result.futureValue mustBe None
      }

      "return None when connector call fails" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

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
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

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
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

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
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

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

    "checkEnrolmentExists" must {
      "return UsersAssignedEnrolment" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

        val usersGroupSearchResponse: UsersGroupResponse = UsersGroupResponse(
          identityProviderType = SCP,
          obfuscatedUserId = Some("********6037"),
          email = Some("email1@test.com"),
          lastAccessedTimestamp = Some("2022-02-27T12:00:27Z"),
          additionalFactors = Some(List(AdditionalFactors("sms", Some("07783924321"))))
        )

        when(mockUsersGroupsSearchConnector.getUserDetails(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[UsersGroupResponse]](
            Future.successful(
              Right(
                Some(usersGroupSearchResponse)
              )
            )
          )
        )

        val result = sut.checkEnrolmentExists("123")
        result.futureValue mustBe a[UsersAssignedEnrolment]
      }

      "return EnrolmentDoesNotExist when no enrolments returned" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

        when(mockUsersGroupsSearchConnector.getUserDetails(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[UsersGroupResponse]](
            Future.successful(
              Right(
                None
              )
            )
          )
        )

        val result = sut.checkEnrolmentExists("123")
        result.futureValue mustBe a[EnrolmentDoesNotExist]
      }

      "return EnrolmentError when the connector returns an error" in {
        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

        when(mockUsersGroupsSearchConnector.getUserDetails(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[UsersGroupResponse]](
            Future.successful(
              Left(
                UpstreamErrorResponse.apply("ERROR", 400)
              )
            )
          )
        )

        val result = sut.checkEnrolmentExists("123")
        result.futureValue mustBe a[EnrolmentError]
      }
    }

    "checkEnrolmentStatus" must {
      "return user details if both matching userIds and userDetails calls are successful" in {

        lazy val sut: EnrolmentStoreCachingService =
          new EnrolmentStoreCachingService(
            mockJourneyCacheRepository,
            mockEnrolmentsConnector,
            mockUsersGroupsSearchConnector
          )

        val usersGroupSearchResponse: UsersGroupResponse = UsersGroupResponse(
          identityProviderType = SCP,
          obfuscatedUserId = Some("********6037"),
          email = Some("email1@test.com"),
          lastAccessedTimestamp = Some("2022-02-27T12:00:27Z"),
          additionalFactors = Some(List(AdditionalFactors("sms", Some("07783924321"))))
        )

        when(mockEnrolmentsConnector.getUserIdsWithEnrolments(any(), any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Seq[String]](
            Future.successful(
              Right(Seq("ID 1", "ID 2", "ID 3"))
            )
          )
        )

        when(mockUsersGroupsSearchConnector.getUserDetails(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[UsersGroupResponse]](
            Future.successful(
              Right(
                Some(usersGroupSearchResponse)
              )
            )
          )
        )
        val expectedResult = UsersAssignedEnrolment(
          AccountDetails(
            usersGroupSearchResponse.identityProviderType,
            "ID 1",
            usersGroupSearchResponse.obfuscatedUserId.getOrElse(""),
            usersGroupSearchResponse.email.map(SensitiveString),
            usersGroupSearchResponse.lastAccessedTimestamp,
            AccountDetails.additionalFactorsToMFADetails(usersGroupSearchResponse.additionalFactors),
            None
          )
        )

        sut.checkEnrolmentStatus("key", "value").futureValue mustBe expectedResult
      }
    }
  }
}
