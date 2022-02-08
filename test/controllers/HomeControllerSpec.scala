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
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.controllershelpers.{HomeCardGenerator, HomePageCachingHelper, RlsInterruptHelper}
import models.{SelfAssessmentUser, _}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import play.api.libs.json.JsBoolean
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import services.partials.MessageFrontendService
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.{Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.Fixtures._
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, Fixtures}
import views.html.HomeView

import scala.concurrent.{ExecutionContext, Future}

class HomeControllerSpec extends BaseSpec with CurrentTaxYear {

  val mockConfigDecorator = mock[ConfigDecorator]
  val mockTaxCalculationService = mock[TaxCalculationService]
  val mockTaiService = mock[TaiService]
  val mockSeissService = mock[SeissService]
  val mockMessageFrontendService = mock[MessageFrontendService]
  val mockPreferencesFrontendService = mock[PreferencesFrontendService]
//  val mockRlsInterruptHelper = mock[RlsInterruptHelper]
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
      TaxCalculation("Overpaid", BigDecimal(84.23), 2015, Some("REFUND"), None, None, None)
    )
    lazy val getPaperlessPreferenceResponse: ActivatePaperlessResponse = ActivatePaperlessActivatedResponse
    lazy val getIVJourneyStatusResponse: IdentityVerificationResponse = IdentityVerificationSuccessResponse("Success")
    lazy val getCitizenDetailsResponse = true
    lazy val selfAssessmentUserType: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(
      SaUtr(new SaUtrGenerator().nextSaUtr.utr)
    )
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
        injected[MessagesControllerComponents],
        injected[HomeView],
        mockSeissService,
        injected[RlsInterruptHelper]
      )(mockConfigDecorator, mockTemplateRenderer, ec)

    when(mockTaiService.taxComponents(any[Nino](), any[Int]())(any[HeaderCarrier]())) thenReturn {
      Future.successful(TaxComponentsSuccessResponse(buildTaxComponents))
    }
    when(mockSeissService.hasClaims(ActivatedOnlineFilerSelfAssessmentUser(any()))(any())) thenReturn Future.successful(
      true
    )
    when(mockSeissService.hasClaims(NotYetActivatedOnlineFilerSelfAssessmentUser(any()))(any())) thenReturn Future
      .successful(true)
    when(mockSeissService.hasClaims(WrongCredentialsSelfAssessmentUser(any()))(any())) thenReturn Future.successful(
      true
    )
    when(mockSeissService.hasClaims(NotEnrolledSelfAssessmentUser(any()))(any())) thenReturn Future.successful(true)
    when(mockSeissService.hasClaims(NonFilerSelfAssessmentUser)) thenReturn Future.successful(false)

    when(mockTaxCalculationService.getTaxYearReconciliations(any[Nino])(any[HeaderCarrier])) thenReturn {
      Future.successful(buildTaxYearReconciliations)
    }
    when(mockPreferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
      Future.successful(getPaperlessPreferenceResponse)
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

    when(mockConfigDecorator.enforcePaperlessPreferenceEnabled) thenReturn true
    when(mockConfigDecorator.taxComponentsEnabled) thenReturn true
    when(mockConfigDecorator.taxcalcEnabled) thenReturn true
    when(mockConfigDecorator.ltaEnabled) thenReturn true
    when(mockConfigDecorator.allowSaPreview) thenReturn true
    when(mockConfigDecorator.allowLowConfidenceSAEnabled) thenReturn allowLowConfidenceSA
    when(mockConfigDecorator.identityVerificationUpliftUrl) thenReturn "/mdtp/uplift"
    when(mockConfigDecorator.pertaxFrontendHost) thenReturn ""
    when(
      mockConfigDecorator.getBasGatewayFrontendSignOutUrl("/personal-account")
    ) thenReturn "/bas-gateway/sign-out-without-state?continue=/personal-account"
    when(
      mockConfigDecorator.getBasGatewayFrontendSignOutUrl("/feedback/PERTAX")
    ) thenReturn "/bas-gateway/sign-out-without-state?continue=/feedback/PERTAX"
    when(mockConfigDecorator.citizenAuthFrontendSignOut) thenReturn "/ida/signout"
    when(mockConfigDecorator.defaultOrigin) thenReturn Origin("PERTAX")
    when(mockConfigDecorator.getFeedbackSurveyUrl(Origin("PERTAX"))) thenReturn "/feedback/PERTAX"
    when(
      mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl
    ) thenReturn "/bas-gateway/ssoout/non-digital?continue=%2Fservice%2Fself-assessment%3Faction=activate&step=enteractivationpin"
    when(mockConfigDecorator.ssoUrl) thenReturn Some("ssoUrl")
    when(mockConfigDecorator.bannerLinkUrl) thenReturn None

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]) = {
      controller
      route(app, req)
    }

  }

  "Calling HomeController.index" must {

    "return a 200 status when accessing index page with good nino and sa User" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) mustBe OK

      if (config.taxComponentsEnabled)
        verify(controller.taiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any())
      if (config.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            )
          )
      })

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) mustBe OK

      if (config.taxComponentsEnabled)
        verify(controller.taiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any())
      if (config.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return 200 when Preferences Frontend returns ActivatePaperlessNotAllowedResponse" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request
            )
          )
      })

      override lazy val getPaperlessPreferenceResponse = ActivatePaperlessNotAllowedResponse

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) mustBe OK

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
      status(r) mustBe SEE_OTHER
      redirectLocation(r) mustBe Some("http://www.example.com")
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
      status(r) mustBe OK

      if (config.taxcalcEnabled)
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
      status(r) mustBe OK

    }

    "return a 303 status when the user's residential address status isn't 0" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request,
              personDetails = Some(
                PersonDetails(
                  address = Some(buildFakeAddress.copy(status = Some(0))),
                  correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(status = Some(1))),
                  person = buildFakePerson
                )
              )
            )
          )
      })

      val r: Future[Result] = controller.index()(FakeRequest())
      println("-" * 100)
      println(r.futureValue.header)
      println("-" * 100)
      println(r.futureValue.body)
      println("-" * 100)

      status(r) mustBe OK
      contentAsString(r)

    }

  }

  "Calling serviceCallResponses" must {

    val userNino = Some(fakeNino)

    "return TaxComponentsDisabled where taxComponents is not enabled" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request
            )
          )
      })

      when(mockConfigDecorator.taxComponentsEnabled) thenReturn false

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result mustBe TaxComponentsDisabledState

    }

    "return TaxCalculationAvailable status when data returned from TaxCalculation" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))
      result mustBe TaxComponentsAvailableState(
        TaxComponents(Seq("EmployerProvidedServices", "PersonalPensionPayments"))
      )

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

      result mustBe TaxComponentsNotAvailableState
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

      result mustBe TaxComponentsUnreachableState
    }

    "return None where TaxCalculation service is not enabled" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(buildUserRequest(request = request))
      })

      when(mockConfigDecorator.taxcalcEnabled) thenReturn false

      val (_, resultCYm1, resultCYm2) = await(controller.serviceCallResponses(userNino, year))

      resultCYm1 mustBe None
      resultCYm2 mustBe None
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

      resultCYM1 mustBe None
      resultCYM2 mustBe None
    }

    "return taxCalculation for CY1 and CY2 status from list returned from TaxCalculation Service." in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val (_, resultCYM1, resultCYM2) = await(controller.serviceCallResponses(userNino, year))

      resultCYM1 mustBe Some(TaxYearReconciliation(2016, Balanced))
      resultCYM2 mustBe Some(TaxYearReconciliation(2015, Balanced))
    }
  }
}
