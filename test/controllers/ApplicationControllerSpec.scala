/*
 * Copyright 2017 HM Revenue & Customs
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
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.bindable.{Origin, StrictContinueUrl}
import models._
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.libs.json.JsBoolean
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services._
import services.partials.{CspPartialService, MessagePartialService}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.time.TaxYearResolver
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.Future


class ApplicationControllerSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[TaiService].toInstance(MockitoSugar.mock[TaiService]))
    .overrides(bind[MessagePartialService].toInstance(MockitoSugar.mock[MessagePartialService]))
    .overrides(bind[CspPartialService].toInstance(MockitoSugar.mock[CspPartialService]))
    .overrides(bind[PreferencesFrontendService].toInstance(MockitoSugar.mock[PreferencesFrontendService]))
    .overrides(bind[IdentityVerificationFrontendService].toInstance(MockitoSugar.mock[IdentityVerificationFrontendService]))
    .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
    .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
    .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
    .overrides(bind[TaxCalculationService].toInstance(MockitoSugar.mock[TaxCalculationService]))
    .overrides(bind[UserDetailsService].toInstance(MockitoSugar.mock[UserDetailsService]))
    .overrides(bind[SelfAssessmentService].toInstance(MockitoSugar.mock[SelfAssessmentService]))
    .overrides(bind[LocalPartialRetriever].toInstance(MockitoSugar.mock[LocalPartialRetriever]))
    .overrides(bind[ConfigDecorator].toInstance(MockitoSugar.mock[ConfigDecorator]))
    .overrides(bind[LocalSessionCache].toInstance(MockitoSugar.mock[LocalSessionCache]))
    .build()

  override def beforeEach: Unit = {
    reset(injected[PertaxAuditConnector], injected[PertaxAuthConnector], injected[TaxCalculationService], injected[CitizenDetailsService],
      injected[TaiService], injected[MessagePartialService],
      injected[UserDetailsService])
  }

  trait LocalSetup {

    lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider
    lazy val nino: Nino = Fixtures.fakeNino
    lazy val personDetailsResponse: PersonDetailsResponse = PersonDetailsSuccessResponse(Fixtures.buildPersonDetails)
    lazy val confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200
    lazy val withPaye: Boolean = true
    lazy val year = TaxYearResolver.currentTaxYear
    lazy val getTaxCalculationResponse: TaxCalculationResponse = TaxCalculationSuccessResponse(TaxCalculation("Overpaid", BigDecimal(84.23), 2015, Some("REFUND"), None))
    lazy val getPaperlessPreferenceResponse: ActivatePaperlessResponse = ActivatePaperlessActivatedResponse
    lazy val getIVJourneyStatusResponse: IdentityVerificationResponse = IdentityVerificationSuccessResponse("Success")
    lazy val getCitizenDetailsResponse = true
    lazy val getSelfAssessmentServiceResponse: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
    lazy val getLtaServiceResponse = Future.successful(true)

    lazy val authority = buildFakeAuthority(nino = nino, withPaye = withPaye, confidenceLevel = confidenceLevel)

    def allowLowConfidenceSA: Boolean

    lazy val controller = {

      val c = injected[ApplicationController]

      when(c.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(authority))
      }
      when(c.taiService.taxSummary(meq(nino), any[Int])(any[HeaderCarrier])) thenReturn {
        Future.successful(TaxSummarySuccessResponse(buildTaxSummary))
      }
      when(c.taxCalculationService.getTaxCalculation(any[Nino], any[Int])(any[HeaderCarrier])) thenReturn {
        Future.successful(TaxCalculationSuccessResponse(buildTaxCalculation))
      }
      when(c.userDetailsService.getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(authProviderType)))
      }
      when(c.citizenDetailsService.personDetails(meq(nino))(any())) thenReturn {
        Future.successful(personDetailsResponse)
      }
      when(c.cspPartialService.webchatClickToChatScriptPartial(any())(any())) thenReturn {
        Future.successful(HtmlPartial.Success(None, Html("<script></script>")))
      }
      when(c.preferencesFrontendService.getPaperlessPreference(any())(any())) thenReturn {
        Future.successful(getPaperlessPreferenceResponse)
      }
      when(c.taxCalculationService.getTaxCalculation(meq(nino), meq(year - 1))(any())) thenReturn {
        Future.successful(getTaxCalculationResponse)
      }
      when(c.identityVerificationFrontendService.getIVJourneyStatus(any())(any())) thenReturn {
        Future.successful(getIVJourneyStatusResponse)
      }
      when(c.selfAssessmentService.getSelfAssessmentUserType(any())(any())) thenReturn {
        Future.successful(getSelfAssessmentServiceResponse)
      }
      when(c.auditConnector.sendEvent(any())(any(), any())) thenReturn {
        Future.successful(AuditResult.Success)
      }
      when(injected[LocalSessionCache].fetch()(any(), any())) thenReturn {
        Future.successful(Some(CacheMap("id", Map("urBannerDismissed" -> JsBoolean(true)))))
      }

      when(c.configDecorator.taxSummaryEnabled) thenReturn true
      when(c.configDecorator.taxcalcEnabled) thenReturn true
      when(c.configDecorator.ltaEnabled) thenReturn true
      when(c.configDecorator.allowSaPreview) thenReturn true
      when(c.configDecorator.allowLowConfidenceSAEnabled) thenReturn allowLowConfidenceSA
      when(c.configDecorator.identityVerificationUpliftUrl) thenReturn "/mdtp/uplift"
      when(c.configDecorator.ivExeptionsEnabled) thenReturn true
      when(c.configDecorator.companyAuthHost) thenReturn ""
      when(c.configDecorator.pertaxFrontendHost) thenReturn ""
      when(c.configDecorator.getCompanyAuthFrontendSignOutUrl("/personal-account")) thenReturn "/gg/sign-out?continue=/personal-account"
      when(c.configDecorator.getCompanyAuthFrontendSignOutUrl("/feedback-survey?origin=PERTAX")) thenReturn "/gg/sign-out?continue=/feedback-survey?origin=PERTAX"
      when(c.configDecorator.citizenAuthFrontendSignOut) thenReturn "/ida/signout"
      when(c.configDecorator.defaultOrigin) thenReturn Origin("PERTAX")
      when(c.configDecorator.getFeedbackSurveyUrl(Origin("PERTAX"))) thenReturn "/feedback-survey?origin=PERTAX"
      when(c.configDecorator.ssoToActivateSaEnrolmentPinUrl) thenReturn "/ssoout/non-digital?continue=%2Fservice%2Fself-assessment%3Faction=activate&step=enteractivationpin"
      when(c.configDecorator.gg_web_context) thenReturn "gg"
      when(c.configDecorator.ssoUrl) thenReturn Some("ssoUrl")
      when(c.configDecorator.urLinkUrl) thenReturn None

      c
    }

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]) = {
      controller //Call to inject mocks
      route(app, req)
    }

  }

  "Calling ApplicationController.uplift" should {

    "send the user to IV using the PTA-TCS origin when the redirectUrl contains 'tax-credits-summary'" in new LocalSetup {

      override lazy val confidenceLevel = ConfidenceLevel.L0
      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser
      override val allowLowConfidenceSA = false

      val r = controller.uplift(Some(StrictContinueUrl("/personal-account/tax-credits-summary")))(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe 303
      redirectLocation(r) shouldBe Some("/mdtp/uplift?origin=PTA-TCS&confidenceLevel=200&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Ftax-credits-summary&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account%252Ftax-credits-summary")

      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
      verify(controller.preferencesFrontendService, times(0)).getPaperlessPreference(any())(any())
    }

    "send the user to IV using the PERTAX origin when the redirectUrl does not contain 'tax-credits-summary'" in new LocalSetup {

      override lazy val confidenceLevel = ConfidenceLevel.L0
      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser
      override val allowLowConfidenceSA = false

      val r = controller.uplift(Some(StrictContinueUrl("/personal-account")))(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe 303
      redirectLocation(r) shouldBe Some("/mdtp/uplift?origin=PERTAX&confidenceLevel=200&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account")

      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
      verify(controller.preferencesFrontendService, times(0)).getPaperlessPreference(any())(any())
    }

    "return BAD_REQUEST status when completionURL is not relative" in new LocalSetup {
      override lazy val confidenceLevel = ConfidenceLevel.L0
      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser
      override val allowLowConfidenceSA = false


      val r = routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/do-uplift?redirectUrl=http://example.com")).get
      status(r) shouldBe BAD_REQUEST
      redirectLocation(r) shouldBe None

      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
      verify(controller.preferencesFrontendService, times(0)).getPaperlessPreference(any())(any())    }
  }

  "Calling ApplicationController.index" should {

    "return a 303 status when accessing index page and authorisation is not fulfilled" in new LocalSetup {
      override val allowLowConfidenceSA = false

      val r = controller.index()(FakeRequest("GET", "/personal-account")) //No auth in this fake request
      status(r) shouldBe 303

      redirectLocation(r) shouldBe Some("/gg/sign-in?continue=%2Fpersonal-account%2Fdo-uplift%3FredirectUrl%3D%252Fpersonal-account&accountType=individual&origin=PERTAX")
    }

    "return a 200 status when accessing index page with good nino and sa User" in new LocalSetup {

      override lazy val nino = Fixtures.fakeNino
      override lazy val authority = buildFakeAuthority(nino = nino, withSa = true, withPaye = withPaye, confidenceLevel = confidenceLevel)
      override val allowLowConfidenceSA = false

      val r = controller.index()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.citizenDetailsService, times(1)).personDetails(meq(nino))(any())
      if(controller.configDecorator.taxSummaryEnabled) verify(controller.taiService, times(1)).taxSummary(meq(Fixtures.fakeNino), meq(TaxYearResolver.currentTaxYear))(any())
      if(controller.configDecorator.taxcalcEnabled) verify(controller.taxCalculationService, times(1)).getTaxCalculation(meq(Fixtures.fakeNino), meq(TaxYearResolver.currentTaxYear - 1))(any())
      verify(controller.userDetailsService, times(1)).getUserDetails(meq("/userDetailsLink"))(any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User" in new LocalSetup {

      override lazy val nino = Fixtures.fakeNino
      override lazy val authority = buildFakeAuthority(nino = nino, withSa = false, withPaye = withPaye, confidenceLevel = confidenceLevel)
      override val allowLowConfidenceSA = false

      val r = controller.index()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.citizenDetailsService, times(1)).personDetails(meq(nino))(any())
      if(controller.configDecorator.taxSummaryEnabled) verify(controller.taiService, times(1)).taxSummary(meq(Fixtures.fakeNino), meq(TaxYearResolver.currentTaxYear))(any())
      if(controller.configDecorator.taxcalcEnabled) verify(controller.taxCalculationService, times(1)).getTaxCalculation(meq(Fixtures.fakeNino), meq(TaxYearResolver.currentTaxYear - 1))(any())
      verify(controller.userDetailsService, times(1)).getUserDetails(meq("/userDetailsLink"))(any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User with no Lta protections" in new LocalSetup {

      override lazy val nino = Fixtures.fakeNino
      override lazy val getLtaServiceResponse = Future.successful(false)
      override lazy val authority = buildFakeAuthority(nino = nino, withSa = false, withPaye = withPaye, confidenceLevel = confidenceLevel)
      override val allowLowConfidenceSA = false

      val r = controller.index()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.citizenDetailsService, times(1)).personDetails(meq(nino))(any())
      if(controller.configDecorator.taxSummaryEnabled) verify(controller.taiService, times(1)).taxSummary(meq(Fixtures.fakeNino), meq(TaxYearResolver.currentTaxYear))(any())
      if(controller.configDecorator.taxcalcEnabled) verify(controller.taxCalculationService, times(1)).getTaxCalculation(meq(Fixtures.fakeNino), meq(TaxYearResolver.currentTaxYear - 1))(any())
      verify(controller.userDetailsService, times(1)).getUserDetails(meq("/userDetailsLink"))(any())
    }

    "return a 423 status when accessing index page with a nino that is hidden in citizen-details with an SA user" in new LocalSetup {

      override lazy val personDetailsResponse = PersonDetailsHiddenResponse
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe LOCKED

      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

    "return a 200 status without calling citizen-detials or tai, when accessing index page without paye account" in new LocalSetup {

      override lazy val withPaye = false
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.citizenDetailsService, times(0)).personDetails(meq(nino))(any())
      verify(controller.taiService, times(0)).taxSummary(any(), meq(TaxYearResolver.currentTaxYear))(any())
    }

    "return 200 when Preferences Frontend returns ActivatePaperlessNotAllowedResponse" in new LocalSetup {

      override lazy val getPaperlessPreferenceResponse = ActivatePaperlessNotAllowedResponse
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK
    }


    "redirect when Preferences Frontend returns ActivatePaperlessRequiresUserActionResponse" in new LocalSetup {

      override lazy val getPaperlessPreferenceResponse = ActivatePaperlessRequiresUserActionResponse("http://www.redirect.com")
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
    }

    "return 200 when TaxCalculationService returns TaxCalculationNotFoundResponse" in new LocalSetup {

      override lazy val getTaxCalculationResponse = TaxCalculationNotFoundResponse
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      if(controller.configDecorator.taxcalcEnabled) verify(controller.taxCalculationService, times(1)).getTaxCalculation(meq(nino), meq(TaxYearResolver.currentTaxYear - 1))(any())

    }

    "return a 200 status when accessing index page with a nino that does not map to any personal deails in citizen-details" in new LocalSetup {

      override lazy val personDetailsResponse = PersonDetailsNotFoundResponse
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK
    }

    "return a 200 status when accessing index page with a nino that produces an error when calling citizen-details" in new LocalSetup {

      override lazy val personDetailsResponse = PersonDetailsErrorResponse(null)
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK
    }
  }

  "Calling ApplicationController.handleSelfAssessment" should {

    "return 303 when called with a GG user that needs to activate their SA enollment." in new LocalSetup {

      override lazy val authority = buildFakeAuthority(nino = nino, withSa = false, withPaye = withPaye, confidenceLevel = confidenceLevel)
      override lazy val getCitizenDetailsResponse = true
      override lazy val getSelfAssessmentServiceResponse = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowLowConfidenceSA = false

      val r = controller.handleSelfAssessment()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER

      redirectLocation(r) shouldBe Some("/ssoout/non-digital?continue=%2Fservice%2Fself-assessment%3Faction=activate&step=enteractivationpin")

    }

    "return 200 when called with a GG user that is SA or has an SA enrollment in another account." in new LocalSetup {

      override lazy val authority = buildFakeAuthority(nino = nino, withSa = true, withPaye = withPaye, confidenceLevel = confidenceLevel)
      override lazy val getCitizenDetailsResponse = true
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowLowConfidenceSA = false

      val r = controller.handleSelfAssessment()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK
    }
  }

  "Calling ApplicationController.showUpliftJourneyOutcome" should {

    "return 200 when IV journey outcome was Success" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Success")
      override val allowLowConfidenceSA = false

      val r = controller.showUpliftJourneyOutcome(Some(StrictContinueUrl("/relative/url")))(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe OK
    }

    "redirect to the IV exempt landing page when the 'sa allow low confidence' feature is on" in new LocalSetup {

      override val allowLowConfidenceSA = true
      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account/sa-continue")
    }

    "return 401 when IV journey outcome was LockedOut" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("LockedOut")
      override val allowLowConfidenceSA = false

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe UNAUTHORIZED
    }

    "redirect to the IV exempt landing page when IV journey outcome was InsufficientEvidence" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("InsufficientEvidence")
      override val allowLowConfidenceSA = false

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account/sa-continue")
    }

    "return 401 when IV journey outcome was UserAborted" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("UserAborted")
      override val allowLowConfidenceSA = false

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe UNAUTHORIZED
    }

    "return 500 when IV journey outcome was TechnicalIssues" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("TechnicalIssues")
      override val allowLowConfidenceSA = false

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe INTERNAL_SERVER_ERROR
    }

    "return 500 when IV journey outcome was Timeout" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Timeout")
      override val allowLowConfidenceSA = false

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe INTERNAL_SERVER_ERROR
    }

    "return bad request when continueUrl is not relative" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Success")
      override val allowLowConfidenceSA = false

      val r = routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/identity-check-complete?continueUrl=http://example.com&journeyId=XXXXX")).get

      status(r) shouldBe BAD_REQUEST
    }

  }

  "Calling ApplicationController.signout" should {

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with a continue URL and no origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider
      override val allowLowConfidenceSA = false

      val r = controller.signout(Some(StrictContinueUrl("/personal-account")), None)(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/gg/sign-out?continue=/personal-account")
    }

    "redirect to verify sign-out link with correct continue url when signed in with verify, a continue URL and no origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.VerifyAuthProvider
      override val allowLowConfidenceSA = false

      val r = controller.signout(Some(StrictContinueUrl("/personal-account")), None)(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/ida/signout")
      session(r).get("postLogoutPage") shouldBe Some("/personal-account")
    }

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with no continue URL but an origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider
      override val allowLowConfidenceSA = false

      val r = controller.signout(None, Some(Origin("PERTAX")))(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/gg/sign-out?continue=/feedback-survey?origin=PERTAX")
    }

    "return BAD_REQUEST when signed in with government gateway with no continue URL and no origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider
      override val allowLowConfidenceSA = false

      val r = controller.signout(None, None)(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe BAD_REQUEST
    }

    "redirect to verify sign-out link with correct continue url when signed in with verify, with no continue URL and but an origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.VerifyAuthProvider
      override val allowLowConfidenceSA = false

      val r = controller.signout(None, Some(Origin("PERTAX")))(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/ida/signout")
      session(r).get("postLogoutPage") shouldBe Some("/feedback-survey?origin=PERTAX")
    }

    "return 'Bad Request' when supplied no continue URL and no origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.VerifyAuthProvider
      override val allowLowConfidenceSA = false

      val r = controller.signout(None, None)(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe BAD_REQUEST
    }

    "return bad request when supplied with a none relative url" in new LocalSetup{

      override lazy val authProviderType: String = UserDetails.VerifyAuthProvider
      override val allowLowConfidenceSA = false

      val r = routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/signout?continueUrl=http://example.com&origin=PERTAX")).get
      status(r) shouldBe BAD_REQUEST
    }
  }


  "Calling ApplicationController.ivExemptLandingPage" should {

    "return 200 for a user who has logged in with GG linked and has a full SA enrollment" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowLowConfidenceSA = false

      val r = controller.ivExemptLandingPage(None)(buildFakeRequestWithAuth("GET"))

      val doc = Jsoup.parse(contentAsString(r))
      status(r) shouldBe OK
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(controller.auditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any()) //TODO - check captured event
    }

    "redirect to the SA activation page on the portal for a user logged in with GG linked to SA which is not yet activated" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowLowConfidenceSA = false

      val r = controller.ivExemptLandingPage(None)(buildFakeRequestWithAuth("GET"))
      val doc = Jsoup.parse(contentAsString(r))
      status(r) shouldBe OK
      doc.getElementsByClass("heading-large").toString().contains("Activate your Self Assessment registration") shouldBe true
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(controller.auditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any()) //TODO - check captured event
    }

    "redirect to 'You cannot access your SA information with this user ID' page for a user who has a SAUtr but logged into the wrong GG account" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))
      override val allowLowConfidenceSA = false

      val r = controller.ivExemptLandingPage(None)(buildFakeRequestWithAuth("GET"))
      val doc = Jsoup.parse(contentAsString(r))
      status(r) shouldBe OK
      doc.getElementsByClass("heading-large").toString().contains("You cannot access your Self Assessment") shouldBe true
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(controller.auditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any()) //TODO - check captured event
    }

    "redirect to 'We cannot confirm your identity' page for a user who has no SAUTR" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser
      override val allowLowConfidenceSA = false

      val r = controller.ivExemptLandingPage(None)(buildFakeRequestWithAuth("GET"))
      val doc = Jsoup.parse(contentAsString(r))
      status(r) shouldBe OK
      doc.getElementsByClass("heading-large").toString().contains("We cannot confirm your identity") shouldBe true
      verify(controller.auditConnector, times(0)).sendEvent(any())(any(), any()) //TODO - check captured event

    }

    "return bad request when continueUrl is not relative" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser
      override val allowLowConfidenceSA = false

      val r = routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/sa-continue?continueUrl=http://example.com")).get

      status(r) shouldBe BAD_REQUEST
    }
  }

}
