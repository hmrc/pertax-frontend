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
import connectors.PertaxAuditConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthAction, AuthJourney, SelfAssessmentStatusAction}
import models._
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.frontend.binders.SafeRedirectUrl
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.Fixtures._
import util.UserRequestFixture.buildUserRequest
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future

class ApplicationControllerSpec extends BaseSpec with CurrentTaxYear with MockitoSugar {

  val mockAuditConnector = mock[PertaxAuditConnector]
  val mockIdentityVerificationFrontendService = mock[IdentityVerificationFrontendService]
  val mockAuthAction = mock[AuthAction]
  val mockSelfAssessmentStatusAction = mock[SelfAssessmentStatusAction]
  val mockAuthJourney = mock[AuthJourney]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[IdentityVerificationFrontendService].toInstance(mockIdentityVerificationFrontendService),
      bind[AuthAction].toInstance(mockAuthAction),
      bind[SelfAssessmentStatusAction].toInstance(mockSelfAssessmentStatusAction),
      bind[AuthJourney].toInstance(mockAuthJourney)
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
      SaUtr("1111111111"))

    val messagesApi = injected[MessagesApi]

    def controller: ApplicationController =
      new ApplicationController(
        messagesApi,
        mockIdentityVerificationFrontendService,
        mockAuthJourney
      )(mockLocalPartialRetriever, injected[ConfigDecorator], injected[TemplateRenderer])

    when(mockIdentityVerificationFrontendService.getIVJourneyStatus(any())(any())) thenReturn {
      Future.successful(getIVJourneyStatusResponse)
    }

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]): Option[Future[Result]] = {
      controller //Call to inject mocks
      route(app, req)
    }
  }

  "Calling ApplicationController.uplift" should {

    "return BAD_REQUEST status when completionURL is not relative" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            ))
      })

      val result =
        routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/do-uplift?redirectUrl=http://example.com")).get

      status(result) shouldBe BAD_REQUEST
      redirectLocation(result) shouldBe None

    }
  }

  "Calling ApplicationController.showUpliftJourneyOutcome" should {

    "return 200 when IV journey outcome was Success" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Success")

      val result = controller.showUpliftJourneyOutcome(Some(SafeRedirectUrl("/relative/url")))(
        buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) shouldBe OK

    }

    "return 401 when IV journey outcome was LockedOut" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("LockedOut")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) shouldBe UNAUTHORIZED

    }

    "redirect to the IV exempt landing page when IV journey outcome was InsufficientEvidence" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("InsufficientEvidence")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/sa-continue")
    }

    "return 401 when IV journey outcome was UserAborted" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("UserAborted")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) shouldBe UNAUTHORIZED

    }

    "return 500 when IV journey outcome was TechnicalIssues" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("TechnicalIssues")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) shouldBe INTERNAL_SERVER_ERROR

    }

    "return 500 when IV journey outcome was Timeout" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Timeout")

      val result = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) shouldBe INTERNAL_SERVER_ERROR

    }

    "return bad request when continueUrl is not relative" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Success")

      val result = routeWrapper(
        buildFakeRequestWithAuth(
          "GET",
          "/personal-account/identity-check-complete?continueUrl=http://example.com&journeyId=XXXXX")).get

      status(result) shouldBe BAD_REQUEST

    }

  }

  "Calling ApplicationController.signout" should {

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with a continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result = controller.signout(Some(SafeRedirectUrl("/personal-account")), None)(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/gg/sign-out?continue=/personal-account")
    }

    "redirect to verify sign-out link with correct continue url when signed in with verify, a continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            ))
      })

      val result = controller.signout(Some(SafeRedirectUrl("/personal-account")), None)(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/ida/signout")
      session(result).get("postLogoutPage") shouldBe Some("/personal-account")
    }

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with no continue URL but an origin" in new LocalSetup {

      when(mockAuthJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result = controller.signout(None, Some(Origin("PERTAX")))(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/gg/sign-out?continue=/feedback/PERTAX")
    }

    "return BAD_REQUEST when signed in with government gateway with no continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider

      val result = controller.signout(None, None)(FakeRequest())
      status(result) shouldBe BAD_REQUEST

    }

    "redirect to verify sign-out link with correct continue url when signed in with verify, with no continue URL and but an origin" in new LocalSetup {

      when(mockAuthJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            ))
      })

      val result = controller.signout(None, Some(Origin("PERTAX")))(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/ida/signout")
      session(result).get("postLogoutPage") shouldBe Some("/feedback/PERTAX")
    }

    "return 'Bad Request' when signed in with verify and supplied no continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            ))
      })

      val result = controller.signout(None, None)(FakeRequest())
      status(result) shouldBe BAD_REQUEST

    }

    "return bad request when supplied with a none relative url" in new LocalSetup {

      when(mockAuthJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            ))
      })

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            ))
      })

      val result = routeWrapper(
        buildFakeRequestWithAuth("GET", "/personal-account/signout?continueUrl=http://example.com&origin=PERTAX")).get
      status(result) shouldBe BAD_REQUEST

    }
  }

  override def now: () => DateTime = DateTime.now
}
