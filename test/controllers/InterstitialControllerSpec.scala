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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import models.{ActivatePaperlessNotAllowedResponse, ActivatePaperlessResponse, ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.mvc.{ActionBuilder, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services._
import services.partials.{FormPartialService, SaPartialService}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.Future

class InterstitialControllerSpec extends BaseSpec with MockitoSugar {

  override lazy val app = localGuiceApplicationBuilder().build()

  trait LocalSetup {

    def simulateFormPartialServiceFailure: Boolean
    def simulateSaPartialServiceFailure: Boolean
    def paperlessResponse: ActivatePaperlessResponse

    lazy val fakeRequest = FakeRequest("", "")

    val mockAuthJourney = mock[AuthJourney]

    def controller: InterstitialController =
      new InterstitialController(
        injected[MessagesApi],
        mock[FormPartialService],
        mock[SaPartialService],
        mock[PreferencesFrontendService],
        mockAuthJourney,
        injected[WithBreadcrumbAction]
      )(mock[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer]) {
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

        when(preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
          Future.successful(paperlessResponse)
        }
      }
  }

  "Calling displayNationalInsurance" should {

    "call FormPartialService.getNationalInsurancePartial and return 200 when called by authorised user " in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequest(
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
              None,
              request))
      })

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      val testController = controller

      val result = testController.displayNationalInsurance(fakeRequest)

      status(result) shouldBe OK

      verify(testController.formPartialService, times(1)).getNationalInsurancePartial(any())

    }
  }

  "Calling displayChildBenefits" should {

    "call FormPartialService.getChildBenefitPartial and return 200 when called by authorised user" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      val fakeRequestWithPath = FakeRequest("GET", "/foo")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequest(
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
              None,
              request))
      })

      val result = controller.displayChildBenefits(fakeRequestWithPath)

      status(result) shouldBe OK

    }
  }

  "Calling viewSelfAssessmentSummary" should {

    "call FormPartialService.getSelfAssessmentPartial and return 200 when called by a high GG user" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequest(
              None,
              None,
              None,
              ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
              "GovernmentGateway",
              ConfidenceLevel.L200,
              None,
              None,
              None,
              None,
              None,
              request))
      })

      val testController = controller
      val r = testController.displaySelfAssessment(fakeRequest)

      status(r) shouldBe OK

      verify(testController.formPartialService, times(1))
        .getSelfAssessmentPartial(any()) //Not called at the min due to DFS bug
    }

    "call FormPartialService.getSelfAssessmentPartial and return return 401 for a high GG user not enrolled in SA" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequest(
              Some(Fixtures.fakeNino),
              None,
              None,
              NonFilerSelfAssessmentUser,
              "GovernmentGateway",
              ConfidenceLevel.L200,
              None,
              None,
              None,
              None,
              None,
              request))
      })

      val testController = controller

      val r = testController.displaySelfAssessment(fakeRequest)
      status(r) shouldBe UNAUTHORIZED

      verify(testController.formPartialService, times(1)).getSelfAssessmentPartial(any())
    }

    "call FormPartialService.getSelfAssessmentPartial and return 401 for a user not logged in via GG" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequest(
              Some(Fixtures.fakeNino),
              None,
              None,
              NonFilerSelfAssessmentUser,
              "Verify",
              ConfidenceLevel.L500,
              None,
              None,
              None,
              None,
              None,
              request))
      })

      val testController = controller

      val r = testController.displaySelfAssessment(fakeRequest)
      status(r) shouldBe UNAUTHORIZED

      verify(testController.formPartialService, times(1)).getSelfAssessmentPartial(any())
    }

    "Calling getSa302" should {

      "should return OK response when accessing with an SA user with a valid tax year" in new LocalSetup {

        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              UserRequest(
                Some(Fixtures.fakeNino),
                None,
                None,
                ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
                "GovernmentGateway",
                ConfidenceLevel.L200,
                None,
                None,
                None,
                None,
                None,
                request
              ))
        })

        val testController = controller

        val r = testController.displaySa302Interrupt(2018)(fakeRequest)

        status(r) shouldBe OK
        contentAsString(r) should include("1111111111")
      }

      "should return UNAUTHORIZED response when accessing with a non SA user with a valid tax year" in new LocalSetup {

        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              UserRequest(
                Some(Fixtures.fakeNino),
                None,
                None,
                NonFilerSelfAssessmentUser,
                "GovernmentGateway",
                ConfidenceLevel.L200,
                None,
                None,
                None,
                None,
                None,
                request))
        })

        val testController = controller
        val r = testController.displaySa302Interrupt(2018)(fakeRequest)

        status(r) shouldBe UNAUTHORIZED
      }
    }
  }
}
