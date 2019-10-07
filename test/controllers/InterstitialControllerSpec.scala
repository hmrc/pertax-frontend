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

package controllers

import connectors.FrontEndDelegationConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.LocalErrorHandler
import models.{ActivatePaperlessNotAllowedResponse, ActivatePaperlessResponse, ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser}
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.mvc.{ActionBuilder, AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services._
import services.partials.{FormPartialService, MessageFrontendService, SaPartialService}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.partials.HtmlPartial
import util.{BaseSpec, Fixtures, MockPertaxDependencies}

import scala.concurrent.Future

class InterstitialControllerSpec extends BaseSpec {

  trait LocalSetup {

    def simulateFormPartialServiceFailure: Boolean
    def simulateSaPartialServiceFailure: Boolean
    def paperlessResponse: ActivatePaperlessResponse

    lazy val fakeRequest = FakeRequest("", "")

    val mockAuthJourney = MockitoSugar.mock[AuthJourney]

    def controller(request: UserRequest[_]) =
      new InterstitialController(
        injected[MessagesApi],
        MockitoSugar.mock[FormPartialService],
        MockitoSugar.mock[SaPartialService],
        MockitoSugar.mock[CitizenDetailsService],
        MockitoSugar.mock[UserDetailsService],
        MockitoSugar.mock[FrontEndDelegationConnector],
        MockitoSugar.mock[PreferencesFrontendService],
        MockitoSugar.mock[MessageFrontendService],
        MockPertaxDependencies,
        injected[LocalErrorHandler],
        mockAuthJourney,
        injected[WithBreadcrumbAction]
      ) {
        private def formPartialServiceResponse = Future.successful {
          if (simulateFormPartialServiceFailure) HtmlPartial.Failure()
          else HtmlPartial.Success(Some("Success"), Html("any"))
        }
        when(formPartialService.getSelfAssessmentPartial(any())) thenReturn formPartialServiceResponse
        when(formPartialService.getNationalInsurancePartial(any())) thenReturn formPartialServiceResponse

        when(saPartialService.getSaAccountSummary(any())) thenReturn {
          Future.successful {
            if (simulateSaPartialServiceFailure) HtmlPartial.Failure()
            else HtmlPartial.Success(Some("Success"), Html("any"))
          }
        }

        when(citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
          Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
        }

        when(preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
          Future.successful(paperlessResponse)
        }
        when(messageFrontendService.getUnreadMessageCount(any())) thenReturn {
          Future.successful(None)
        }
        when(configDecorator.taxCreditsEnabled) thenReturn true
        when(configDecorator.ssoUrl) thenReturn Some("ssoUrl")
        when(configDecorator.getFeedbackSurveyUrl(any())) thenReturn "/test"
        when(configDecorator.analyticsToken) thenReturn Some("N/A")

      }
  }

  "Calling displayNationalInsurance" should {

    "call FormPartialService.getNationalInsurancePartial and return 200 when called by authorised user " in new LocalSetup {

      lazy val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        None,
        NonFilerSelfAssessmentUser,
        "SomeAuth",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        fakeRequest)

      when(mockAuthJourney.auth).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(userRequest.asInstanceOf[UserRequest[A]])
      })

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      val testController = controller(userRequest)

      val result = testController.displayNationalInsurance(userRequest)

      status(result) shouldBe OK

      verify(testController.formPartialService, times(1)).getNationalInsurancePartial(any())

    }
  }

  "Calling displayChildBenefits" should {

    "call FormPartialService.getChildBenefitPartial and return 200 when called by authorised user who is high gg" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse
      lazy val userRequest = UserRequest(
        None,
        None,
        None,
        NonFilerSelfAssessmentUser,
        "SomeAuth",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        fakeRequest)

      val testController = controller(userRequest)

      val r = testController.displayChildBenefits(userRequest)
      status(r) shouldBe OK

      verify(testController.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(testController.citizenDetailsService, times(0)).personDetails(meq(Fixtures.fakeNino))(any())
    }
  }

  "Calling viewSelfAssessmentSummary" should {

    "call FormPartialService.getSelfAssessmentPartial and return 200 when called by a high GG user" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse
      lazy val userRequest = UserRequest(
        None,
        None,
        None,
        ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
        "SomeAuth",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        fakeRequest)

      val testController = controller(userRequest)

      val r = testController.displaySelfAssessment(userRequest)
      status(r) shouldBe OK

      verify(testController.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(testController.formPartialService, times(1))
        .getSelfAssessmentPartial(any()) //Not called at the min due to DFS bug
      verify(testController.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "call FormPartialService.getSelfAssessmentPartial and return return 401 for a high GG user not enrolled in SA" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse
      lazy val userRequest = UserRequest(
        None,
        None,
        None,
        NonFilerSelfAssessmentUser,
        "SomeAuth",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        fakeRequest)

      val testController = controller(userRequest)

      val r = testController.displaySelfAssessment(userRequest)
      status(r) shouldBe UNAUTHORIZED

      verify(testController.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(testController.formPartialService, times(1)).getSelfAssessmentPartial(any())
      verify(testController.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "call FormPartialService.getSelfAssessmentPartial and return 401 for a user not logged in via GG" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse
      lazy val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        None,
        NonFilerSelfAssessmentUser,
        "SomeAuth",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        fakeRequest)

      val testController = controller(userRequest)

      val r = testController.displaySelfAssessment(userRequest)
      status(r) shouldBe UNAUTHORIZED

      verify(testController.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(testController.formPartialService, times(1)).getSelfAssessmentPartial(any())
      verify(testController.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

    "Calling getSa302" should {

      "should return OK response when accessing with an SA user with a valid tax year" in new LocalSetup {

        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse
        lazy val userRequest = UserRequest(
          Some(Fixtures.fakeNino),
          None,
          None,
          ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
          "SomeAuth",
          ConfidenceLevel.L200,
          None,
          None,
          None,
          None,
          fakeRequest
        )

        val testController = controller(userRequest)

        val r = testController.displaySa302Interrupt(2014)(userRequest)
        status(r) shouldBe OK

        verify(testController.messageFrontendService, times(1)).getUnreadMessageCount(any())
      }

      "should return UNAUTHORIZED response when accessing with a none SA user with a valid tax year" in new LocalSetup {

        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse
        lazy val userRequest = UserRequest(
          Some(Fixtures.fakeNino),
          None,
          None,
          NonFilerSelfAssessmentUser,
          "SomeAuth",
          ConfidenceLevel.L200,
          None,
          None,
          None,
          None,
          fakeRequest)

        val testController = controller(userRequest)

        val r = testController.displaySa302Interrupt(2014)(userRequest)
        status(r) shouldBe UNAUTHORIZED

        verify(testController.messageFrontendService, times(1)).getUnreadMessageCount(any())
      }
    }
  }
}
