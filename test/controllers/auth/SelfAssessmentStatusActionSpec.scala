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

import controllers.auth.requests.{AuthenticatedRequest, RefinedRequest, SelfAssessmentEnrolment}
import models.MatchingDetails
import org.mockito.Matchers._
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
import uk.gov.hmrc.domain.{Nino, SaUtr}

import scala.concurrent.Future

class SelfAssessmentStatusActionSpec
    extends FreeSpec with MustMatchers with MockitoSugar with BeforeAndAfterEach with GuiceOneAppPerSuite {

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[CitizenDetailsService].toInstance(mockCitizenDetailsService))
    .overrides(bind[AuthAction].toInstance(mockAuthAction))
    .build()

  val mockAuthAction = mock[AuthAction]
  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]

  override def beforeEach: Unit =
    reset(mockCitizenDetailsService)

  def harness[A]()(implicit request: AuthenticatedRequest[A]): Future[Result] = {

    lazy val actionProvider = app.injector.instanceOf[SelfAssessmentStatusAction]

    actionProvider.invokeBlock(
      request, { refinedRequest: RefinedRequest[_] =>
        Future.successful(
          Ok(s"Nino: ${refinedRequest.nino.getOrElse("fail").toString}, SaUtr: ${refinedRequest.saUserType.toString}")
        )
      }
    )
  }

  "An SA user with an activated enrolment must" - {

    "return ActivatedOnlineFilerSelfAssessmentUser" in {
      implicit val request: AuthenticatedRequest[AnyContent] = AuthenticatedRequest(
        Some(Nino("AB123456C")),
        Some(SelfAssessmentEnrolment(SaUtr("1111111111"), "Activated")),
        "foo",
        FakeRequest())
      val result = harness()(request)
      contentAsString(result) must include("ActivatedOnlineFilerSelfAssessmentUser")
      verify(mockCitizenDetailsService, times(0)).getMatchingDetails(any())(any())
    }
  }
  "An SA user with a not yet activated enrolment must" - {
    "return NotYetActivatedOnlineFilerSelfAssessmentUser" in {
      implicit val request: AuthenticatedRequest[AnyContent] = AuthenticatedRequest(
        Some(Nino("AB123456C")),
        Some(SelfAssessmentEnrolment(SaUtr("1111111111"), "NotYetActivated")),
        "foo",
        FakeRequest())
      val result = harness()(request)
      contentAsString(result) must include("NotYetActivatedOnlineFilerSelfAssessmentUser")
      verify(mockCitizenDetailsService, times(0)).getMatchingDetails(any())(any())
    }
  }

  "A user without an SA enrolment must" - {
    "when CitizenDetails has a matching SA account" - {
      "return AmbiguousFilerSelfAssessmentUser" in {
        implicit val request: AuthenticatedRequest[AnyContent] =
          AuthenticatedRequest(Some(Nino("AB123456C")), None, "foo", FakeRequest())

        when(mockCitizenDetailsService.getMatchingDetails(any())(any()))
          .thenReturn(Future.successful(MatchingDetailsSuccessResponse(MatchingDetails(Some(SaUtr("1111111111"))))))
        val result = harness()(request)
        contentAsString(result) must include("AmbiguousFilerSelfAssessmentUser")
        verify(mockCitizenDetailsService, times(1)).getMatchingDetails(any())(any())
      }
    }

    "when CitizenDetails has no matching SA account" - {
      "return NonFilerSelfAssessmentUser" in {
        implicit val request: AuthenticatedRequest[AnyContent] =
          AuthenticatedRequest(Some(Nino("AB123456C")), None, "foo", FakeRequest())

        when(mockCitizenDetailsService.getMatchingDetails(any())(any()))
          .thenReturn(Future.successful(MatchingDetailsNotFoundResponse))
        val result = harness()(request)
        contentAsString(result) must include("NonFilerSelfAssessmentUser")
        verify(mockCitizenDetailsService, times(1)).getMatchingDetails(any())(any())
      }
    }

  }

}
