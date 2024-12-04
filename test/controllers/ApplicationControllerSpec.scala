/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.EitherT
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthRetrievals, SelfAssessmentStatusAction}
import controllers.bindable.Origin
import models._
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import testUtils.Fixtures._
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.time.CurrentTaxYear

import java.time.LocalDate
import scala.concurrent.Future

class ApplicationControllerSpec extends BaseSpec with CurrentTaxYear {

  val mockAuditConnector: AuditConnector                                           = mock[AuditConnector]
  val mockIdentityVerificationFrontendService: IdentityVerificationFrontendService =
    mock[IdentityVerificationFrontendService]
  val mockAuthAction: AuthRetrievals                                               = mock[AuthRetrievals]
  val mockSelfAssessmentStatusAction: SelfAssessmentStatusAction                   = mock[SelfAssessmentStatusAction]
  val mockInterstitialController: InterstitialController                           = mock[InterstitialController]
  val mockHomeController: HomeController                                           = mock[HomeController]
  val mockRlsConfirmAddressController: RlsController                               = mock[RlsController]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[IdentityVerificationFrontendService].toInstance(mockIdentityVerificationFrontendService),
      bind[AuthRetrievals].toInstance(mockAuthAction),
      bind[SelfAssessmentStatusAction].toInstance(mockSelfAssessmentStatusAction),
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[HomeController].toInstance(mockHomeController),
      bind[RlsController].toInstance(mockRlsConfirmAddressController)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockIdentityVerificationFrontendService,
      mockAuthAction,
      mockSelfAssessmentStatusAction,
      mockAuthJourney
    )
  }

  trait LocalSetup {

    lazy val authProviderType: String                                                                         = UserDetails.GovernmentGatewayAuthProvider
    lazy val nino: Nino                                                                                       = Fixtures.fakeNino
    lazy val personDetailsResponse: PersonDetails                                                             = Fixtures.buildPersonDetails
    lazy val withPaye: Boolean                                                                                = true
    lazy val year: Int                                                                                        = current.currentYear
    lazy val getIVJourneyStatusResponse: EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
      EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(Success)))
    lazy val getCitizenDetailsResponse                                                                        = true
    lazy val getSelfAssessmentServiceResponse: SelfAssessmentUserType                                         = ActivatedOnlineFilerSelfAssessmentUser(
      SaUtr(new SaUtrGenerator().nextSaUtr.utr)
    )

    lazy val controller: ApplicationController = app.injector.instanceOf[ApplicationController]

    when(mockIdentityVerificationFrontendService.getIVJourneyStatus(any())(any(), any())) thenReturn {
      getIVJourneyStatusResponse
    }

    def routeWrapper(req: FakeRequest[AnyContentAsEmpty.type]): Option[Future[Result]] = {
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

      val result: Future[Result] =
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

      val result: Future[Result] =
        routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/do-uplift?redirectUrl=http://example.com")).get

      status(result) mustBe BAD_REQUEST
      redirectLocation(result) mustBe None

    }

    "return a redirect response with the provided URL if redirectUrl is defined" in new LocalSetup {
      val redirectUrl: Option[RedirectUrl] = Some(RedirectUrl("/redirect_url"))

      val result: Future[Result] = controller.uplift(redirectUrl)(FakeRequest())

      status(result)           must equal(SEE_OTHER)
      redirectLocation(result) must equal(Some("/redirect_url"))
    }

    "return a redirect response to HomeController index if redirectUrl is not defined" in new LocalSetup {
      val redirectUrl: Option[Nothing] = None

      val result: Future[Result] = controller.uplift(redirectUrl)(FakeRequest())

      status(result)           must equal(SEE_OTHER)
      redirectLocation(result) must equal(Some(routes.HomeController.index.url))
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

      val result: Future[Result] = controller.showUpliftJourneyOutcome(Some(RedirectUrl("/relative/url")))(
        buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX")
      )

      status(result) mustBe OK
    }

    "return 200 when IV journey outcome was Success without continueUrl " in new LocalSetup {
      val redirect: String = routes.HomeController.index.url

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result: Future[Result] = controller.showUpliftJourneyOutcome(Some(RedirectUrl(redirect)))(
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

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(LockedOut)))

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe UNAUTHORIZED

    }

    "redirect to the IV exempt landing page when IV journey outcome was InsufficientEvidence" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](
          Future.successful(Right(InsufficientEvidence))
        )

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
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

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(UserAborted)))

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe UNAUTHORIZED

    }

    "return 401 when IV journey outcome was FailedMatching" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(FailedMatching)))

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe UNAUTHORIZED

    }

    "return 401 when IV journey outcome was Incomplete" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(Incomplete)))

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe UNAUTHORIZED

    }

    "return 401 when IV journey outcome was PrecondFailed" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(PrecondFailed)))

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe UNAUTHORIZED

    }

    "return 500 when IV journey outcome was TechnicalIssues" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(TechnicalIssue)))

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe INTERNAL_SERVER_ERROR

    }

    "return 401 when IV journey outcome was Timeout" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(Timeout)))

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe UNAUTHORIZED

    }

    "return 500 when IV journey outcome was not recognised" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(InvalidResponse)))

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe INTERNAL_SERVER_ERROR

    }

    "return 400 to the IvTechnicalIssuesView if unable to get IV journey status" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](
          Future.successful(Left(UpstreamErrorResponse.apply("Bad request", 400)))
        )

      val result: Future[Result] =
        controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe BAD_REQUEST

    }

    "return bad request when continueUrl is not relative" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result: Future[Result] = routeWrapper(
        buildFakeRequestWithAuth(
          "GET",
          "/personal-account/identity-check-complete?continueUrl=http://example.com&journeyId=XXXXX"
        )
      ).get

      status(result) mustBe BAD_REQUEST

    }

  }

  "Calling ApplicationController.signout" must {

    "redirect to sign-out link with correct continue url when signed in with with a continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result: Future[Result] = controller.signout(Some(RedirectUrl("/personal-account")), None)(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        "http://localhost:9553/bas-gateway/sign-out-without-state?continue=/personal-account"
      )
    }

    "redirect to sign-out link with correct continue url when signed in with no continue URL but an origin" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result: Future[Result] = controller.signout(None, Some(Origin("TESTORIGIN")))(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        "http://localhost:9553/bas-gateway/sign-out-without-state?continue=http://localhost:9514/feedback/TESTORIGIN"
      )
    }

    "return BAD_REQUEST when signed in with no continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider

      val result: Future[Result] = controller.signout(None, None)(FakeRequest())
      status(result) mustBe BAD_REQUEST

    }

    "return 'Bad Request' when signed in with verify and supplied no continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            )
          )
      })

      val result: Future[Result] = controller.signout(None, None)(FakeRequest())
      status(result) mustBe BAD_REQUEST

    }

    "return see other when supplied with an absolute url" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", ""),
              confidenceLevel = ConfidenceLevel.L200,
              request = request
            )
          )
      })

      val sentLocation           = "http://example.com&origin=PERTAX"
      val result: Future[Result] = controller.signout(Some(RedirectUrl(sentLocation)), None)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        config.getBasGatewayFrontendSignOutUrl("http://localhost:9514/feedback/PERTAX")
      )
      session(result).get("postLogoutPage") mustBe None
    }
  }

  override def now: () => LocalDate = () => LocalDate.now()
}
