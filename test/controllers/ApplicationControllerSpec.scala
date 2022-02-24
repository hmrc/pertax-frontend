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
import connectors.{PersonDetailsResponse, PersonDetailsSuccessResponse}
import controllers.address.RlsConfirmAddressController
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthAction, AuthJourney, SelfAssessmentStatusAction}
import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.Application
import play.api.inject._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.bootstrap.binders.{RedirectUrl, SafeRedirectUrl}
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.Fixtures._
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, Fixtures}
import views.html.iv.failure._
import views.html.iv.success.SuccessView

import scala.concurrent.{ExecutionContext, Future}

class ApplicationControllerSpec extends BaseSpec with CurrentTaxYear {

  val mockAuditConnector = mock[AuditConnector]
  val mockIdentityVerificationFrontendService = mock[IdentityVerificationFrontendService]
  val mockAuthAction = mock[AuthAction]
  val mockSelfAssessmentStatusAction = mock[SelfAssessmentStatusAction]
  val mockAuthJourney = mock[AuthJourney]
  val mockInterstitialController = mock[InterstitialController]
  val mockHomeController = mock[HomeController]
  val mockRlsConfirmAddressController = mock[RlsConfirmAddressController]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[IdentityVerificationFrontendService].toInstance(mockIdentityVerificationFrontendService),
      bind[AuthAction].toInstance(mockAuthAction),
      bind[SelfAssessmentStatusAction].toInstance(mockSelfAssessmentStatusAction),
      bind[AuthJourney].toInstance(mockAuthJourney),
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[HomeController].toInstance(mockHomeController),
      bind[RlsConfirmAddressController].toInstance(mockRlsConfirmAddressController)
    )
    .build()

  override def beforeEach: Unit =
    reset(
      mockIdentityVerificationFrontendService,
      mockAuthAction,
      mockSelfAssessmentStatusAction,
      mockAuthJourney
    )

  trait LocalSetup {

    lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider
    lazy val nino: Nino = Fixtures.fakeNino
    lazy val personDetailsResponse: PersonDetailsResponse = PersonDetailsSuccessResponse(Fixtures.buildPersonDetails)
    lazy val withPaye: Boolean = true
    lazy val year = current.currentYear
    lazy val getIVJourneyStatusResponse: IdentityVerificationResponse = IdentityVerificationSuccessResponse("Success")
    lazy val getCitizenDetailsResponse = true
    lazy val getSelfAssessmentServiceResponse: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(
      SaUtr(new SaUtrGenerator().nextSaUtr.utr)
    )

    def controller: ApplicationController =
      new ApplicationController(
        mockIdentityVerificationFrontendService,
        mockAuthJourney,
        injected[MessagesControllerComponents],
        injected[SuccessView],
        injected[CannotConfirmIdentityView],
        injected[FailedIvIncompleteView],
        injected[LockedOutView],
        injected[TimeOutView],
        injected[TechnicalIssuesView]
      )(config, templateRenderer, ec)

    when(mockIdentityVerificationFrontendService.getIVJourneyStatus(any())(any())) thenReturn {
      Future.successful(getIVJourneyStatusResponse)
    }

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]): Option[Future[Result]] = {
      controller //Call to inject mocks
      route(app, req)
    }
  }

  "Calling ApplicationController.uplift" must {

    "return BAD_REQUEST status when completionURL is empty" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            )
          )
      })

      val result =
        routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/do-uplift?redirectUrl=")).get

      status(result) mustBe BAD_REQUEST
      redirectLocation(result) mustBe None

    }

    "return BAD_REQUEST status when completionURL is not relative" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            )
          )
      })

      val result =
        routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/do-uplift?redirectUrl=http://example.com")).get

      status(result) mustBe BAD_REQUEST
      redirectLocation(result) mustBe None

    }
  }

  "Calling ApplicationController.showUpliftJourneyOutcome" must {

    "return 200 when IV journey outcome was Success" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Success")

      val result = controller.showUpliftJourneyOutcome(Some(SafeRedirectUrl("/relative/url")))(
        buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX")
      )
      status(result) mustBe OK

    }

    "return 401 when IV journey outcome was LockedOut" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("LockedOut")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe UNAUTHORIZED

    }

    "redirect to the IV exempt landing page when IV journey outcome was InsufficientEvidence" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("InsufficientEvidence")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/sa-continue")
    }

    "return 401 when IV journey outcome was UserAborted" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("UserAborted")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe UNAUTHORIZED

    }

    "return 500 when IV journey outcome was TechnicalIssues" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("TechnicalIssues")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe INTERNAL_SERVER_ERROR

    }

    "return 500 when IV journey outcome was Timeout" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Timeout")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe INTERNAL_SERVER_ERROR

    }

    "return bad request when continueUrl is not relative" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Success")

      val result = routeWrapper(
        buildFakeRequestWithAuth(
          "GET",
          "/personal-account/identity-check-complete?continueUrl=http://example.com&journeyId=XXXXX"
        )
      ).get

      status(result) mustBe BAD_REQUEST

    }

  }

  "Calling ApplicationController.signout" must {

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with a continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result = controller.signout(Some(RedirectUrl("/personal-account")), None)(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        "http://localhost:9553/bas-gateway/sign-out-without-state?continue=/personal-account"
      )
    }
    "redirect to verify sign-out link with correct continue url when signed in with verify, a continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            )
          )
      })

      val result = controller.signout(Some(RedirectUrl("/personal-account")), None)(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("http://localhost:9949/ida/signout")
      session(result).get("postLogoutPage") mustBe Some("/personal-account")
    }

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with no continue URL but an origin" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result = controller.signout(None, Some(Origin("TESTORIGIN")))(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        "http://localhost:9553/bas-gateway/sign-out-without-state?continue=http://localhost:9514/feedback/TESTORIGIN"
      )
    }

    "return BAD_REQUEST when signed in with government gateway with no continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider

      val result = controller.signout(None, None)(FakeRequest())
      status(result) mustBe BAD_REQUEST

    }

    "redirect to verify sign-out link with correct continue url when signed in with verify, with no continue URL and but an origin" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            )
          )
      })

      val result = controller.signout(None, Some(Origin("PERTAX")))(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("http://localhost:9949/ida/signout")
      session(result).get("postLogoutPage") mustBe Some("http://localhost:9514/feedback/PERTAX")
    }

    "return 'Bad Request' when signed in with verify and supplied no continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            )
          )
      })

      val result = controller.signout(None, None)(FakeRequest())
      status(result) mustBe BAD_REQUEST

    }

    "return see other when supplied with an absolute url" in new LocalSetup {

      when(mockAuthJourney.minimumAuthWithSelfAssessment).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            )
          )
      })

      val result = controller.signout(Some(RedirectUrl("http://example.com&origin=PERTAX")), None)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(config.citizenAuthFrontendSignOut)
      session(result).get("postLogoutPage") mustBe Some("http://localhost:9514/feedback/PERTAX")
    }
  }

  override def now: () => DateTime = DateTime.now
}
