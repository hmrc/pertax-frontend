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
import controllers.auth.{AuthAction, AuthJourney, SelfAssessmentStatusAction}
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
import views.html.iv.failure._

import java.time.LocalDate
import scala.concurrent.Future

class UpliftControllerSpec extends BaseSpec with CurrentTaxYear {

  val mockAuditConnector: AuditConnector                                           = mock[AuditConnector]
  val mockIdentityVerificationFrontendService: IdentityVerificationFrontendService =
    mock[IdentityVerificationFrontendService]
  val mockAuthAction: AuthAction                                                   = mock[AuthAction]
  val mockSelfAssessmentStatusAction: SelfAssessmentStatusAction                   = mock[SelfAssessmentStatusAction]
  val mockAuthJourney: AuthJourney                                                 = mock[AuthJourney]
  val mockInterstitialController: InterstitialController                           = mock[InterstitialController]
  val mockHomeController: HomeController                                           = mock[HomeController]
  val mockRlsConfirmAddressController: RlsController                               = mock[RlsController]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[IdentityVerificationFrontendService].toInstance(mockIdentityVerificationFrontendService),
      bind[AuthAction].toInstance(mockAuthAction),
      bind[SelfAssessmentStatusAction].toInstance(mockSelfAssessmentStatusAction),
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[HomeController].toInstance(mockHomeController),
      bind[RlsController].toInstance(mockRlsConfirmAddressController)
    )
    .build()

  override def beforeEach(): Unit =
    reset(
      mockIdentityVerificationFrontendService,
      mockAuthAction,
      mockSelfAssessmentStatusAction,
      mockAuthJourney
    )

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

    def controller: UpliftController =
      new UpliftController(
        mockIdentityVerificationFrontendService,
        mockAuthJourney,
        injected[MessagesControllerComponents],
        injected[CannotConfirmIdentityView],
        injected[FailedIvIncompleteView],
        injected[TechnicalIssuesView]
      )(config, ec)

    when(mockIdentityVerificationFrontendService.getIVJourneyStatus(any())(any(), any())) thenReturn {
      getIVJourneyStatusResponse
    }

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]): Option[Future[Result]] = {
      controller //Call to inject mocks
      route(app, req)
    }
  }

  "Calling the UpliftController uplift" must {

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

  "Calling the UpliftController showUpliftFailedJourneyOutcome" must {

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

      val result = controller.showUpliftFailedJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/sa-continue")
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

      val result = controller.showUpliftFailedJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe INTERNAL_SERVER_ERROR

    }

    "return 401 when IV journey outcome was PreconditionFailed" in new LocalSetup {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getIVJourneyStatusResponse
        : EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
        EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(PrecondFailed)))

      val result = controller.showUpliftFailedJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
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

      val result = controller.showUpliftFailedJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(result) mustBe UNAUTHORIZED

    }

    "return bad request when continueUrl is not relative" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result = routeWrapper(
        buildFakeRequestWithAuth(
          "GET",
          "/personal-account/identity-check-complete?continueUrl=http://example.com&journeyId=XXXXX"
        )
      ).get

      status(result) mustBe BAD_REQUEST

    }

  }

  "Calling the UpliftController signout" must {

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with a continue URL and no origin" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
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

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with no continue URL but an origin" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
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

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider

      val result = controller.signout(None, None)(FakeRequest())
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

      val result = controller.signout(None, None)(FakeRequest())
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

      val sentLocation = "http://example.com&origin=PERTAX"
      val result       = controller.signout(Some(RedirectUrl(sentLocation)), None)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        config.getBasGatewayFrontendSignOutUrl("http://localhost:9514/feedback/PERTAX")
      )
      session(result).get("postLogoutPage") mustBe None
    }
  }

  override def now: () => LocalDate = () => LocalDate.now()
}
