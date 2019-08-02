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
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import models._
import org.joda.time.DateTime
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
import services.partials.{CspPartialService, MessageFrontendService}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.time.CurrentTaxYear
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.Future

class HomeControllerSpec extends BaseSpec with CurrentTaxYear {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[TaiService].toInstance(MockitoSugar.mock[TaiService]))
    .overrides(bind[MessageFrontendService].toInstance(MockitoSugar.mock[MessageFrontendService]))
    .overrides(bind[CspPartialService].toInstance(MockitoSugar.mock[CspPartialService]))
    .overrides(bind[PreferencesFrontendService].toInstance(MockitoSugar.mock[PreferencesFrontendService]))
    .overrides(bind[IdentityVerificationFrontendService].toInstance(
      MockitoSugar.mock[IdentityVerificationFrontendService]))
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

  override def beforeEach: Unit =
    reset(
      injected[PertaxAuditConnector],
      injected[PertaxAuthConnector],
      injected[TaxCalculationService],
      injected[CitizenDetailsService],
      injected[TaiService],
      injected[MessageFrontendService],
      injected[UserDetailsService]
    )

  override def now: () => DateTime = DateTime.now

  trait LocalSetup {

    lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider
    lazy val nino: Nino = Fixtures.fakeNino
    lazy val personDetailsResponse: PersonDetailsResponse = PersonDetailsSuccessResponse(Fixtures.buildPersonDetails)
    lazy val confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200
    lazy val withPaye: Boolean = true
    lazy val year = current.currentYear
    lazy val getTaxCalculationResponse: TaxCalculationResponse = TaxCalculationSuccessResponse(
      TaxCalculation("Overpaid", BigDecimal(84.23), 2015, Some("REFUND"), None, None, None))
    lazy val getPaperlessPreferenceResponse: ActivatePaperlessResponse = ActivatePaperlessActivatedResponse
    lazy val getIVJourneyStatusResponse: IdentityVerificationResponse = IdentityVerificationSuccessResponse("Success")
    lazy val getCitizenDetailsResponse = true
    lazy val getSelfAssessmentServiceResponse: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(
      SaUtr("1111111111"))
    lazy val getLtaServiceResponse = Future.successful(true)

    lazy val authority = buildFakeAuthority(nino = nino, withPaye = withPaye, confidenceLevel = confidenceLevel)

    def allowLowConfidenceSA: Boolean

    lazy val controller = {

      val c = injected[HomeController]

      when(c.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(authority))
      }
      when(c.taiService.taxComponents(meq(nino), any[Int])(any[HeaderCarrier])) thenReturn {
        Future.successful(TaxComponentsSuccessResponse(buildTaxComponents))
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
      when(injected[MessageFrontendService].getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }

      when(c.configDecorator.taxComponentsEnabled) thenReturn true
      when(c.configDecorator.taxcalcEnabled) thenReturn true
      when(c.configDecorator.ltaEnabled) thenReturn true
      when(c.configDecorator.allowSaPreview) thenReturn true
      when(c.configDecorator.allowLowConfidenceSAEnabled) thenReturn allowLowConfidenceSA
      when(c.configDecorator.identityVerificationUpliftUrl) thenReturn "/mdtp/uplift"
      when(c.configDecorator.companyAuthHost) thenReturn ""
      when(c.configDecorator.pertaxFrontendHost) thenReturn ""
      when(c.configDecorator.getCompanyAuthFrontendSignOutUrl("/personal-account")) thenReturn "/gg/sign-out?continue=/personal-account"
      when(c.configDecorator.getCompanyAuthFrontendSignOutUrl("/feedback/PERTAX")) thenReturn "/gg/sign-out?continue=/feedback/PERTAX"
      when(c.configDecorator.citizenAuthFrontendSignOut) thenReturn "/ida/signout"
      when(c.configDecorator.defaultOrigin) thenReturn Origin("PERTAX")
      when(c.configDecorator.getFeedbackSurveyUrl(Origin("PERTAX"))) thenReturn "/feedback/PERTAX"
      when(c.configDecorator.ssoToActivateSaEnrolmentPinUrl) thenReturn "/ssoout/non-digital?continue=%2Fservice%2Fself-assessment%3Faction=activate&step=enteractivationpin"
      when(c.configDecorator.gg_web_context) thenReturn "gg-sign-in"
      when(c.configDecorator.ssoUrl) thenReturn Some("ssoUrl")
      when(c.configDecorator.urLinkUrl) thenReturn None
      when(c.configDecorator.analyticsToken) thenReturn Some("N/A")

      c
    }

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]) = {
      controller //Call to inject mocks
      route(app, req)
    }

  }

  "Calling HomeController.index" should {

    "return a 303 status when accessing index page and authorisation is not fulfilled" in new LocalSetup {
      override val allowLowConfidenceSA = false

      val r = controller.index()(FakeRequest("GET", "/personal-account")) //No auth in this fake request

      status(r) shouldBe 303
      redirectLocation(r) shouldBe Some(
        "/gg-sign-in?continue=%2Fpersonal-account%2Fdo-uplift%3FredirectUrl%3D%252Fpersonal-account&accountType=individual&origin=PERTAX")
    }

    "return a 200 status when accessing index page with good nino and sa User" in new LocalSetup {

      override lazy val nino = Fixtures.fakeNino
      override lazy val authority =
        buildFakeAuthority(nino = nino, withSa = true, withPaye = withPaye, confidenceLevel = confidenceLevel)
      override val allowLowConfidenceSA = false

      val r = controller.index()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(nino))(any())
      if (controller.configDecorator.taxComponentsEnabled)
        verify(controller.taiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any())
      if (controller.configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1))
          .getTaxCalculation(meq(Fixtures.fakeNino), meq(current.currentYear - 1))(any())
      verify(controller.userDetailsService, times(1)).getUserDetails(meq("/userDetailsLink"))(any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User" in new LocalSetup {

      override lazy val nino = Fixtures.fakeNino
      override lazy val authority =
        buildFakeAuthority(nino = nino, withSa = false, withPaye = withPaye, confidenceLevel = confidenceLevel)
      override val allowLowConfidenceSA = false

      val r = controller.index()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(nino))(any())
      if (controller.configDecorator.taxComponentsEnabled)
        verify(controller.taiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any())
      if (controller.configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1))
          .getTaxCalculation(meq(Fixtures.fakeNino), meq(current.currentYear - 1))(any())
      verify(controller.userDetailsService, times(1)).getUserDetails(meq("/userDetailsLink"))(any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User with no Lta protections" in new LocalSetup {

      override lazy val nino = Fixtures.fakeNino
      override lazy val getLtaServiceResponse = Future.successful(false)
      override lazy val authority =
        buildFakeAuthority(nino = nino, withSa = false, withPaye = withPaye, confidenceLevel = confidenceLevel)
      override val allowLowConfidenceSA = false

      val r = controller.index()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(nino))(any())
      if (controller.configDecorator.taxComponentsEnabled)
        verify(controller.taiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any())
      if (controller.configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1))
          .getTaxCalculation(meq(Fixtures.fakeNino), meq(current.currentYear - 1))(any())
      verify(controller.userDetailsService, times(1)).getUserDetails(meq("/userDetailsLink"))(any())
    }

    "return a 423 status when accessing index page with a nino that is hidden in citizen-details with an SA user" in new LocalSetup {

      override lazy val personDetailsResponse = PersonDetailsHiddenResponse
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe LOCKED

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(Fixtures.fakeNino))(any())
    }

    "return a 200 status without calling citizen-detials or tai, when accessing index page without paye account" in new LocalSetup {

      override lazy val withPaye = false
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.citizenDetailsService, times(0)).personDetails(meq(nino))(any())
      verify(controller.taiService, times(0)).taxComponents(any(), meq(current.currentYear))(any())
    }

    "return 200 when Preferences Frontend returns ActivatePaperlessNotAllowedResponse" in new LocalSetup {

      override lazy val getPaperlessPreferenceResponse = ActivatePaperlessNotAllowedResponse
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "redirect when Preferences Frontend returns ActivatePaperlessRequiresUserActionResponse" in new LocalSetup {

      override lazy val getPaperlessPreferenceResponse =
        ActivatePaperlessRequiresUserActionResponse("http://www.example.com")
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("http://www.example.com")
    }

    "return 200 when TaxCalculationService returns TaxCalculationNotFoundResponse" in new LocalSetup {

      override lazy val getTaxCalculationResponse = TaxCalculationNotFoundResponse
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      if (controller.configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1))
          .getTaxCalculation(meq(nino), meq(current.currentYear - 1))(any())
    }

    "return a 200 status when accessing index page with a nino that does not map to any personal details in citizen-details" in new LocalSetup {

      override lazy val personDetailsResponse = PersonDetailsNotFoundResponse
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "return a 200 status when accessing index page with a nino that produces an error when calling citizen-details" in new LocalSetup {

      override lazy val personDetailsResponse = PersonDetailsErrorResponse(null)
      override val allowLowConfidenceSA = false

      val r = controller.index(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }
  }
}
