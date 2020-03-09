/*
 * Copyright 2020 HM Revenue & Customs
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
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.controllershelpers.{HomeCardGenerator, HomePageCachingHelper}
import models._
import org.joda.time.DateTime
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.libs.json.JsBoolean
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import services.partials.MessageFrontendService
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.Fixtures._
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, Fixtures, UserRequestFixture}

import scala.concurrent.{ExecutionContext, Future}

class HomeControllerSpec extends BaseSpec with CurrentTaxYear with MockitoSugar {

  val mockConfigDecorator = mock[ConfigDecorator]
  val mockTaxCalculationService = mock[TaxCalculationService]
  val mockTaiService = mock[TaiService]
  val mockMessageFrontendService = mock[MessageFrontendService]
  val mockPreferencesFrontendService = mock[PreferencesFrontendService]
  val mockIdentityVerificationFrontendService = mock[IdentityVerificationFrontendService]
  val mockLocalSessionCache = mock[LocalSessionCache]
  val mockAuthJourney = mock[AuthJourney]
  val mockTemplateRenderer = mock[TemplateRenderer]

  override def beforeEach: Unit =
    reset(
      mockConfigDecorator,
      mockTaxCalculationService,
      mockTaiService,
      mockMessageFrontendService
    )

  override def now: () => DateTime = DateTime.now

  trait LocalSetup {

    lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider
    lazy val nino: Nino = Fixtures.fakeNino
    lazy val personDetailsResponse: PersonDetailsResponse = PersonDetailsSuccessResponse(Fixtures.buildPersonDetails)
    lazy val confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200
    lazy val withPaye: Boolean = true
    lazy val year = 2017
    lazy val getTaxCalculationResponse: TaxCalculationResponse = TaxCalculationSuccessResponse(
      TaxCalculation("Overpaid", BigDecimal(84.23), 2015, Some("REFUND"), None, None, None))
    lazy val getPaperlessPreferenceResponse: ActivatePaperlessResponse = ActivatePaperlessActivatedResponse
    lazy val getIVJourneyStatusResponse: IdentityVerificationResponse = IdentityVerificationSuccessResponse("Success")
    lazy val getCitizenDetailsResponse = true
    lazy val selfAssessmentUserType: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(
      SaUtr("1111111111"))
    lazy val getLtaServiceResponse = Future.successful(true)

    lazy val allowLowConfidenceSA = false

    def controller =
      new HomeController(
        mockPreferencesFrontendService,
        mockTaiService,
        mockTaxCalculationService,
        injected[HomeCardGenerator],
        injected[HomePageCachingHelper],
        mockAuthJourney,
        injected[WithActiveTabAction],
        injected[MessagesControllerComponents]
      )(mockLocalPartialRetriever, mockConfigDecorator, mockTemplateRenderer, injected[ExecutionContext])

    when(mockTaiService.taxComponents(any[Nino](), any[Int]())(any[HeaderCarrier]())) thenReturn {
      Future.successful(TaxComponentsSuccessResponse(buildTaxComponents))
    }
    when(mockTaxCalculationService.getTaxCalculation(any[Nino], any[Int])(any[HeaderCarrier])) thenReturn {
      Future.successful(TaxCalculationSuccessResponse(buildTaxCalculation))
    }
    when(mockTaxCalculationService.getTaxYearReconciliations(any[Nino])(any[HeaderCarrier])) thenReturn {
      Future.successful(buildTaxYearReconciliations)
    }
    when(mockPreferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
      Future.successful(getPaperlessPreferenceResponse)
    }
    when(mockTaxCalculationService.getTaxCalculation(meq(nino), meq(year - 1))(any())) thenReturn {
      Future.successful(getTaxCalculationResponse)
    }
    when(mockIdentityVerificationFrontendService.getIVJourneyStatus(any())(any())) thenReturn {
      Future.successful(getIVJourneyStatusResponse)
    }

    when(mockLocalSessionCache.fetch()(any(), any())) thenReturn {
      Future.successful(Some(CacheMap("id", Map("urBannerDismissed" -> JsBoolean(true)))))
    }
    when(mockMessageFrontendService.getUnreadMessageCount(any())) thenReturn {
      Future.successful(None)
    }

    when(mockConfigDecorator.taxComponentsEnabled) thenReturn true
    when(mockConfigDecorator.taxcalcEnabled) thenReturn true
    when(mockConfigDecorator.ltaEnabled) thenReturn true
    when(mockConfigDecorator.allowSaPreview) thenReturn true
    when(mockConfigDecorator.allowLowConfidenceSAEnabled) thenReturn allowLowConfidenceSA
    when(mockConfigDecorator.identityVerificationUpliftUrl) thenReturn "/mdtp/uplift"
    when(mockConfigDecorator.companyAuthHost) thenReturn ""
    when(mockConfigDecorator.pertaxFrontendHost) thenReturn ""
    when(mockConfigDecorator.getCompanyAuthFrontendSignOutUrl("/personal-account")) thenReturn "/gg/sign-out?continue=/personal-account"
    when(mockConfigDecorator.getCompanyAuthFrontendSignOutUrl("/feedback/PERTAX")) thenReturn "/gg/sign-out?continue=/feedback/PERTAX"
    when(mockConfigDecorator.citizenAuthFrontendSignOut) thenReturn "/ida/signout"
    when(mockConfigDecorator.defaultOrigin) thenReturn Origin("PERTAX")
    when(mockConfigDecorator.getFeedbackSurveyUrl(Origin("PERTAX"))) thenReturn "/feedback/PERTAX"
    when(mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl) thenReturn "/ssoout/non-digital?continue=%2Fservice%2Fself-assessment%3Faction=activate&step=enteractivationpin"
    when(mockConfigDecorator.gg_web_context) thenReturn "gg-sign-in"
    when(mockConfigDecorator.ssoUrl) thenReturn Some("ssoUrl")
    when(mockConfigDecorator.urLinkUrl) thenReturn None
    when(mockConfigDecorator.analyticsToken) thenReturn Some("N/A")

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]) = {
      controller
      route(app, req)
    }

  }

  "Calling HomeController.index" should {

    val configDecorator = injected[ConfigDecorator]
    "return a 200 status when accessing index page with good nino and sa User" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

      if (configDecorator.taxComponentsEnabled)
        verify(controller.taiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any())
      if (configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            ))
      })

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

      if (configDecorator.taxComponentsEnabled)
        verify(controller.taiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any())
      if (configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return 200 when Preferences Frontend returns ActivatePaperlessNotAllowedResponse" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request
            ))
      })

      override lazy val getPaperlessPreferenceResponse = ActivatePaperlessNotAllowedResponse

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

    }

    "redirect when Preferences Frontend returns ActivatePaperlessRequiresUserActionResponse" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      override lazy val getPaperlessPreferenceResponse =
        ActivatePaperlessRequiresUserActionResponse("http://www.example.com")

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("http://www.example.com")
    }

    "return 200 when TaxCalculationService returns TaxCalculationNotFoundResponse" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })
      override lazy val getTaxCalculationResponse = TaxCalculationNotFoundResponse

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

      if (configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return a 200 status when accessing index page with a nino that does not map to any personal details in citizen-details" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

    }

  }

  "Calling serviceCallResponses" should {

    val userNino = Some(fakeNino)

    "return TaxComponentsDisabled where taxComponents is not enabled" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request
            ))
      })

      when(mockConfigDecorator.taxComponentsEnabled) thenReturn false

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result shouldBe TaxComponentsDisabledState

    }

    "return TaxCalculationAvailable status when data returned from TaxCalculation" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))
      result shouldBe TaxComponentsAvailableState(
        TaxComponents(Seq("EmployerProvidedServices", "PersonalPensionPayments")))

    }

    "return TaxComponentsNotAvailableState status when TaxComponentsUnavailableResponse from TaxComponents" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      when(mockTaiService.taxComponents(any[Nino], any[Int])(any[HeaderCarrier])) thenReturn {
        Future.successful(TaxComponentsUnavailableResponse)
      }

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result shouldBe TaxComponentsNotAvailableState
    }

    "return TaxComponentsUnreachableState status when there is TaxComponents returns an unexpected response" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      when(mockTaiService.taxComponents(any[Nino], any[Int])(any[HeaderCarrier])) thenReturn {
        Future.successful(TaxComponentsUnexpectedResponse(HttpResponse(INTERNAL_SERVER_ERROR)))
      }

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result shouldBe TaxComponentsUnreachableState
    }

    "return None where TaxCalculation service is not enabled" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(buildUserRequest(request = request))
      })

      when(mockConfigDecorator.taxcalcEnabled) thenReturn false

      val (_, resultCYm1, resultCYm2) = await(controller.serviceCallResponses(userNino, year))

      resultCYm1 shouldBe None
      resultCYm2 shouldBe None
    }

    "return only  CY-1 None and CY-2 None when get TaxYearReconcillation returns Nil" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      when(mockTaxCalculationService.getTaxYearReconciliations(any())(any())) thenReturn Future.successful(Nil)

      val (_, resultCYM1, resultCYM2) = await(controller.serviceCallResponses(userNino, year))

      resultCYM1 shouldBe None
      resultCYM2 shouldBe None
    }

    "return taxCalculation for CY1 and CY2 status from list returned from TaxCalculation Service." in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val (_, resultCYM1, resultCYM2) = await(controller.serviceCallResponses(userNino, year))

      resultCYM1 shouldBe Some(TaxYearReconciliation(2016, Balanced))
      resultCYM2 shouldBe Some(TaxYearReconciliation(2015, Balanced))
    }
  }
}
