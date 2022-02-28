/*
 * Copyright 2022 HM Revenue & Customs
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
import error.ErrorRenderer
import models.{ActivatePaperlessNotAllowedResponse, ActivatePaperlessResponse, ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.partials.{FormPartialService, SaPartialService}
import services.{NinoDisplayService, _}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util._
import views.html.SelfAssessmentSummaryView
import views.html.interstitial.{ViewChildBenefitsSummaryInterstitialView, ViewNationalInsuranceInterstitialHomeView, ViewNewsAndUpdatesView}
import views.html.selfassessment.Sa302InterruptView

import scala.concurrent.{ExecutionContext, Future}

class InterstitialControllerSpec extends BaseSpec {

  override lazy val app = localGuiceApplicationBuilder().build()

  trait LocalSetup {

    def simulateFormPartialServiceFailure: Boolean
    def simulateSaPartialServiceFailure: Boolean
    def paperlessResponse: ActivatePaperlessResponse

    lazy val fakeRequest = FakeRequest("", "")

    val mockAuthJourney = mock[AuthJourney]
    val ninoDisplayService = mock[NinoDisplayService]

    def controller: InterstitialController =
      new InterstitialController(
        mock[FormPartialService],
        mock[SaPartialService],
        mock[PreferencesFrontendService],
        ninoDisplayService,
        mockAuthJourney,
        injected[WithBreadcrumbAction],
        injected[MessagesControllerComponents],
        injected[ErrorRenderer],
        injected[ViewNationalInsuranceInterstitialHomeView],
        injected[ViewChildBenefitsSummaryInterstitialView],
        injected[SelfAssessmentSummaryView],
        injected[Sa302InterruptView],
        injected[ViewNewsAndUpdatesView]
      )(config, templateRenderer, ec) {
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

        when(ninoDisplayService.getNino(any(), any())).thenReturn(Future.successful(Some(Fixtures.fakeNino)))
      }
  }

  "Calling displayNationalInsurance" must {

    "call FormPartialService.getNationalInsurancePartial and return 200 when called by authorised user " in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              credentials = Credentials("", "Verify"),
              request = request
            )
          )
      })

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      val testController = controller

      val result = testController.displayNationalInsurance(fakeRequest)

      status(result) mustBe OK

      verify(testController.formPartialService, times(1)).getNationalInsurancePartial(any())
    }
  }

  "Calling displayChildBenefits" must {

    "call FormPartialService.getChildBenefitPartial and return 200 when called by authorised user" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      val fakeRequestWithPath = FakeRequest("GET", "/foo")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              credentials = Credentials("", "Verify"),
              request = request
            )
          )
      })

      val result = controller.displayChildBenefits(fakeRequestWithPath)

      status(result) mustBe OK

    }
  }

  "Calling viewSelfAssessmentSummary" must {

    "call FormPartialService.getSelfAssessmentPartial and return 200 when called by a high GG user" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val testController = controller
      val r = testController.displaySelfAssessment(fakeRequest)

      status(r) mustBe OK

      verify(testController.formPartialService, times(1))
        .getSelfAssessmentPartial(any())
    }

    "call FormPartialService.getSelfAssessmentPartial and return return 401 for a high GG user not enrolled in SA" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            )
          )
      })

      val testController = controller

      val r = testController.displaySelfAssessment(fakeRequest)
      status(r) mustBe UNAUTHORIZED
    }

    "call FormPartialService.getSelfAssessmentPartial and return 401 for a user not logged in via GG" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            )
          )
      })

      val testController = controller

      val r = testController.displaySelfAssessment(fakeRequest)
      status(r) mustBe UNAUTHORIZED
    }

    "Calling getSa302" must {

      "should return OK response when accessing with an SA user with a valid tax year" in new LocalSetup {

        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

        val saUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

        def userRequest[A](request: Request[A]) = buildUserRequest(
          saUser = ActivatedOnlineFilerSelfAssessmentUser(saUtr),
          request = request
        )

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              userRequest(request = request)
            )
        })

        val testController = controller

        val r = testController.displaySa302Interrupt(2018)(fakeRequest)

        status(r) mustBe OK
        contentAsString(r) must include(saUtr.utr)
      }

      "should return UNAUTHORIZED response when accessing with a non SA user with a valid tax year" in new LocalSetup {

        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(
                saUser = NonFilerSelfAssessmentUser,
                request = request
              )
            )
        })

        val testController = controller
        val r = testController.displaySa302Interrupt(2018)(fakeRequest)

        status(r) mustBe UNAUTHORIZED
      }
    }
  }

  "Calling displayNewsAndUpdates" must {

    "call displayNewsAndUpdates and return 200 when called by authorised user using verify" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              credentials = Credentials("", "Verify"),
              request = request
            )
          )
      })

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      val testController = controller

      val result = testController.displayNewsAndUpdates(fakeRequest)

      status(result) mustBe OK

      contentAsString(result) must include("News and updates")
    }

    "call displayNewsAndUpdates and return 200 when called by authorised user using GG" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      val testController = controller

      val result = testController.displayNewsAndUpdates(fakeRequest)

      status(result) mustBe OK

      contentAsString(result) must include("News and updates")
    }
  }
}
