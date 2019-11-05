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

package controllers.auth

import connectors.EnrolmentsConnector
import controllers.auth.requests._
import models.MatchingDetails
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
import services.{CitizenDetailsService, MatchingDetailsNotFoundResponse, MatchingDetailsSuccessResponse}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, SaUtr}

import scala.concurrent.Future

class SelfAssessmentStatusActionSpec
    extends FreeSpec with MustMatchers with MockitoSugar with BeforeAndAfterEach with GuiceOneAppPerSuite {

  val saUtr = SaUtr("1111111111")

  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  val enrolmentsConnector = mock[EnrolmentsConnector]

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[CitizenDetailsService].toInstance(mockCitizenDetailsService))
    .overrides(bind[EnrolmentsConnector].toInstance(enrolmentsConnector))
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

  "An SA user with an activated enrolment must" - {

    "return ActivatedOnlineFilerSelfAssessmentUser" in {
      implicit val request: AuthenticatedRequest[AnyContent] = AuthenticatedRequest(
        Some(Nino("AB123456C")),
        Some(SelfAssessmentEnrolment(SaUtr("1111111111"), Activated)),
        Credentials("", "Verify"),
        ConfidenceLevel.L200,
        None,
        None,
        None,
        FakeRequest()
      )

      val result = harness()(request)
      contentAsString(result) must include(s"ActivatedOnlineFilerSelfAssessmentUser(${saUtr.utr})")
      verify(mockCitizenDetailsService, times(0)).getMatchingDetails(any())(any())
    }
  }
  "An SA user with a not yet activated enrolment must" - {
    "return NotYetActivatedOnlineFilerSelfAssessmentUser" in {
      implicit val request = AuthenticatedRequest(
        Some(Nino("AB123456C")),
        Some(SelfAssessmentEnrolment(SaUtr("1111111111"), NotYetActivated)),
        Credentials("", "Verify"),
        ConfidenceLevel.L200,
        None,
        None,
        None,
        FakeRequest()
      )

      val result = harness()(request)
      contentAsString(result) must include(s"NotYetActivatedOnlineFilerSelfAssessmentUser(${saUtr.utr})")
      verify(mockCitizenDetailsService, times(0)).getMatchingDetails(any())(any())
    }
  }

  "A user without an SA enrolment must" - {
    "when CitizenDetails has a matching SA account" - {
      "and they have an enrolment on another credential" - {
        "return WrongCredentialsSelfAssessmentUser" in {
          implicit val request: AuthenticatedRequest[AnyContent] =
            AuthenticatedRequest(
              Some(Nino("AB123456C")),
              None,
              Credentials("", "Verify"),
              ConfidenceLevel.L200,
              None,
              None,
              None,
              FakeRequest())

          val saUtr = SaUtr("1111111111")

          when(mockCitizenDetailsService.getMatchingDetails(any())(any()))
            .thenReturn(Future.successful(MatchingDetailsSuccessResponse(MatchingDetails(Some(saUtr)))))
          when(enrolmentsConnector.getUserIdsWithEnrolments(meq(saUtr.utr))(any(), any()))
            .thenReturn(Future.successful(Seq("some cred id")))

          val result = harness()(request)
          contentAsString(result) must include(s"WrongCredentialsSelfAssessmentUser(${saUtr.utr})")
        }
      }

      "and they have no SA enrolment" - {
        "return NotEnrolledSelfAssessmentUser" in {
          implicit val request: AuthenticatedRequest[AnyContent] =
            AuthenticatedRequest(
              Some(Nino("AB123456C")),
              None,
              Credentials("", "Verify"),
              ConfidenceLevel.L200,
              None,
              None,
              None,
              FakeRequest())

          when(mockCitizenDetailsService.getMatchingDetails(any())(any()))
            .thenReturn(Future.successful(MatchingDetailsSuccessResponse(MatchingDetails(Some(saUtr)))))
          when(enrolmentsConnector.getUserIdsWithEnrolments(meq(saUtr.utr))(any(), any()))
            .thenReturn(Future.successful(Seq.empty))

          val result = harness()(request)
          contentAsString(result) must include(s"NotEnrolledSelfAssessmentUser(${saUtr.utr})")
        }
      }
    }

    "when CitizenDetails has no matching SA account" - {
      "return NonFilerSelfAssessmentUser" in {
        implicit val request: AuthenticatedRequest[AnyContent] =
          AuthenticatedRequest(
            Some(Nino("AB123456C")),
            None,
            Credentials("", "Verify"),
            ConfidenceLevel.L200,
            None,
            None,
            None,
            FakeRequest())

        when(mockCitizenDetailsService.getMatchingDetails(any())(any()))
          .thenReturn(Future.successful(MatchingDetailsNotFoundResponse))
        val result = harness()(request)
        contentAsString(result) must include("NonFilerSelfAssessmentUser")
        verify(mockCitizenDetailsService, times(1)).getMatchingDetails(any())(any())
      }
    }

    "when user has no Nino" - {
      "return NonFilerSelfAssessmentUser" in {
        implicit val request: AuthenticatedRequest[AnyContent] =
          AuthenticatedRequest(
            None,
            None,
            Credentials("", "Verify"),
            ConfidenceLevel.L50,
            None,
            None,
            None,
            FakeRequest())

        val result = harness()(request)
        contentAsString(result) must include("NonFilerSelfAssessmentUser")
        verify(mockCitizenDetailsService, times(0)).getMatchingDetails(any())(any())
      }
    }
  }
}
