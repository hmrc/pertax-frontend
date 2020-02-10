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

package controllers.auth

import connectors.EnrolmentsConnector
import controllers.auth.requests.{Activated, AuthenticatedRequest, NotYetActivated, SelfAssessmentEnrolment, UserRequest}
import models.{ActivatedOnlineFilerSelfAssessmentUser, MatchingDetails, NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, NotYetActivatedOnlineFilerSelfAssessmentUser, SelfAssessmentUserType, WrongCredentialsSelfAssessmentUser}
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FreeSpec, MustMatchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CitizenDetailsService, EnrolmentStoreCachingService, LocalSessionCache, MatchingDetailsNotFoundResponse, MatchingDetailsSuccessResponse}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.Future

class SelfAssessmentStatusActionSpec
    extends FreeSpec with MustMatchers with MockitoSugar with BeforeAndAfterEach with GuiceOneAppPerSuite {

  val saUtr = SaUtr("1111111111")

  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  val enrolmentsCachingService = mock[EnrolmentStoreCachingService]

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[CitizenDetailsService].toInstance(mockCitizenDetailsService))
    .overrides(bind[EnrolmentStoreCachingService].toInstance(enrolmentsCachingService))
    .configure(Map("metrics.enabled" -> false))
    .build()

  override def beforeEach: Unit =
    reset(mockCitizenDetailsService)

  def harness[A]()(implicit request: AuthenticatedRequest[A]): Future[Result] = {

    lazy val actionProvider = app.injector.instanceOf[SelfAssessmentStatusAction]

    actionProvider.invokeBlock(
      request, { userRequest: UserRequest[_] =>
        Future.successful(
          Ok(s"Nino: ${userRequest.nino.getOrElse("fail").toString}, SaUtr: ${userRequest.saUserType.toString}")
        )
      }
    )

  }

  def createAuthenticatedRequest(
    saEnrolment: Option[SelfAssessmentEnrolment],
    nino: Option[Nino] = Some(Nino("AB123456C"))): AuthenticatedRequest[AnyContent] =
    AuthenticatedRequest(
      nino,
      saEnrolment,
      Credentials("", "Verify"),
      ConfidenceLevel.L200,
      None,
      None,
      None,
      None,
      Set.empty,
      FakeRequest()
    )

  "An SA user with an activated enrolment must" - {
    "return ActivatedOnlineFilerSelfAssessmentUser" in {
      val saEnrolment = Some(SelfAssessmentEnrolment(SaUtr("1111111111"), Activated))
      implicit val request = createAuthenticatedRequest(saEnrolment)

      val result = harness()(request)
      contentAsString(result) must include(s"ActivatedOnlineFilerSelfAssessmentUser(${saUtr.utr})")
      verify(mockCitizenDetailsService, times(0)).getMatchingDetails(any())(any())
    }
  }

  "An SA user with a not yet activated enrolment must" - {
    "return NotYetActivatedOnlineFilerSelfAssessmentUser" in {
      val saEnrolment = Some(SelfAssessmentEnrolment(SaUtr("1111111111"), NotYetActivated))
      implicit val request = createAuthenticatedRequest(saEnrolment)

      val result = harness()(request)
      contentAsString(result) must include(s"NotYetActivatedOnlineFilerSelfAssessmentUser(${saUtr.utr})")
      verify(mockCitizenDetailsService, times(0)).getMatchingDetails(any())(any())
    }
  }

  "A user without an SA enrolment must" - {
    "when CitizenDetails has a matching SA account" - {

      val saUtr = SaUtr("1111111111")

      val userTypeList: List[(SelfAssessmentUserType, String)] = List(
        (WrongCredentialsSelfAssessmentUser(saUtr), "a Wrong credentials SA user"),
        (NotEnrolledSelfAssessmentUser(saUtr), "a Not Enrolled SA user"),
        (NonFilerSelfAssessmentUser, "a Non Filer SA user")
      )

      implicit val request = createAuthenticatedRequest(None)

      userTypeList.foreach {
        case (userType, key) =>
          s"return $key when the enrolments caching service returns ${userType.toString}" in {

            when(mockCitizenDetailsService.getMatchingDetails(any())(any()))
              .thenReturn(Future.successful(MatchingDetailsSuccessResponse(MatchingDetails(Some(saUtr)))))

            when(enrolmentsCachingService.getSaUserTypeFromCache(any())(any(), any()))
              .thenReturn(Future.successful(userType))

            val result = harness()(request)
            contentAsString(result) must include(s"${userType.toString}")
          }
      }
    }
  }

  "when CitizenDetails has no matching SA account" - {
    "return NonFilerSelfAssessmentUser" in {
      implicit val request = createAuthenticatedRequest(None)

      when(mockCitizenDetailsService.getMatchingDetails(any())(any()))
        .thenReturn(Future.successful(MatchingDetailsNotFoundResponse))
      val result = harness()(request)
      contentAsString(result) must include("NonFilerSelfAssessmentUser")
      verify(mockCitizenDetailsService, times(1)).getMatchingDetails(any())(any())
    }
  }

  "when user has no Nino" - {
    "return NonFilerSelfAssessmentUser" in {
      implicit val request = createAuthenticatedRequest(None, None)

      val result = harness()(request)
      contentAsString(result) must include("NonFilerSelfAssessmentUser")
      verify(mockCitizenDetailsService, times(0)).getMatchingDetails(any())(any())
    }
  }
}
