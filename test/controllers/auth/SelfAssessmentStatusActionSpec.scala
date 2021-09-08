/*
 * Copyright 2021 HM Revenue & Customs
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

import controllers.auth.requests._
import models._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CitizenDetailsService, EnrolmentStoreCachingService, MatchingDetailsNotFoundResponse, MatchingDetailsSuccessResponse}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, SaUtr, SaUtrGenerator}

import scala.concurrent.Future

class SelfAssessmentStatusActionSpec
    extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach
    with GuiceOneAppPerSuite {

  val saUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  val mockCitizenDetailsService: CitizenDetailsService =
    mock[CitizenDetailsService]
  val enrolmentsCachingService = mock[EnrolmentStoreCachingService]

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService)
    )
    .overrides(
      bind[EnrolmentStoreCachingService].toInstance(enrolmentsCachingService)
    )
    .configure(Map("metrics.enabled" -> false))
    .build()

  override def beforeEach: Unit =
    reset(mockCitizenDetailsService)

  def harness[A]()(implicit
    request: AuthenticatedRequest[A]
  ): Future[Result] = {

    lazy val actionProvider =
      app.injector.instanceOf[SelfAssessmentStatusAction]

    actionProvider.invokeBlock(
      request,
      { userRequest: UserRequest[_] =>
        Future.successful(
          Ok(
            s"Nino: ${userRequest.nino.getOrElse("fail").toString}, SaUtr: ${userRequest.saUserType.toString}"
          )
        )
      }
    )

  }

  def createAuthenticatedRequest(
    saEnrolment: Option[SelfAssessmentEnrolment],
    nino: Option[Nino] = Some(Nino("AB123456C"))
  ): AuthenticatedRequest[AnyContent] =
    AuthenticatedRequest(
      nino,
      saEnrolment,
      Credentials("", "Verify"),
      ConfidenceLevel.L200,
      None,
      None,
      None,
      Set.empty,
      FakeRequest()
    )

  "An SA user with an activated enrolment must" - {
    "return ActivatedOnlineFilerSelfAssessmentUser" in {
      val saEnrolment = Some(SelfAssessmentEnrolment(saUtr, Activated))
      implicit val request = createAuthenticatedRequest(saEnrolment)

      val result = harness()(request)
      contentAsString(result) must include(
        s"ActivatedOnlineFilerSelfAssessmentUser(${saUtr.utr})"
      )
      verify(mockCitizenDetailsService, times(0))
        .getMatchingDetails(any())(any())
    }
  }

  "An SA user with a not yet activated enrolment must" - {
    "return NotYetActivatedOnlineFilerSelfAssessmentUser" in {
      val saEnrolment = Some(SelfAssessmentEnrolment(saUtr, NotYetActivated))
      implicit val request = createAuthenticatedRequest(saEnrolment)

      val result = harness()(request)
      contentAsString(result) must include(
        s"NotYetActivatedOnlineFilerSelfAssessmentUser(${saUtr.utr})"
      )
      verify(mockCitizenDetailsService, times(0))
        .getMatchingDetails(any())(any())
    }
  }

  "A user without an SA enrolment must" - {
    "when CitizenDetails has a matching SA account" - {

      val saUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

      val userTypeList: List[(SelfAssessmentUserType, String)] = List(
        (
          WrongCredentialsSelfAssessmentUser(saUtr),
          "a Wrong credentials SA user"
        ),
        (NotEnrolledSelfAssessmentUser(saUtr), "a Not Enrolled SA user"),
        (NonFilerSelfAssessmentUser, "a Non Filer SA user")
      )

      implicit val request = createAuthenticatedRequest(None)

      userTypeList.foreach {
        case (userType, key) =>
          s"return $key when the enrolments caching service returns ${userType.toString}" in {

            when(mockCitizenDetailsService.getMatchingDetails(any())(any()))
              .thenReturn(
                Future.successful(
                  MatchingDetailsSuccessResponse(MatchingDetails(Some(saUtr)))
                )
              )

            when(
              enrolmentsCachingService
                .getSaUserTypeFromCache(any())(any(), any())
            ).thenReturn(Future.successful(userType))

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
      verify(mockCitizenDetailsService, times(1))
        .getMatchingDetails(any())(any())
    }
  }

  "when user has no Nino" - {
    "return NonFilerSelfAssessmentUser" in {
      implicit val request = createAuthenticatedRequest(None, None)

      val result = harness()(request)
      contentAsString(result) must include("NonFilerSelfAssessmentUser")
      verify(mockCitizenDetailsService, times(0))
        .getMatchingDetails(any())(any())
    }
  }
}
