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
import connectors.{PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.requests.UserRequest
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
import services.partials.MessageFrontendService
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.time.CurrentTaxYear
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever, UserRequestFixture}

import scala.concurrent.Future

class HomeControllerSpec extends BaseSpec with CurrentTaxYear with MockitoSugar {

  override def beforeEach: Unit =
    reset(
      injected[PertaxAuditConnector],
      injected[PertaxAuthConnector],
      injected[TaxCalculationService],
      injected[CitizenDetailsService],
      injected[TaiService],
      injected[MessageFrontendService],
      injected[UserDetailsService],
      injected[ConfigDecorator]
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

    lazy val controller = {

      val c = injected[HomeController]

      when(c.taiService.taxComponents(meq(nino), any[Int])(any[HeaderCarrier])) thenReturn {
        Future.successful(TaxComponentsSuccessResponse(buildTaxComponents))
      }
      when(c.taxCalculationService.getTaxCalculation(any[Nino], any[Int])(any[HeaderCarrier])) thenReturn {
        Future.successful(TaxCalculationSuccessResponse(buildTaxCalculation))
      }
      when(c.taxCalculationService.getTaxYearReconciliations(any[Nino])(any[HeaderCarrier])) thenReturn {
        Future.successful(buildTaxYearReconciliations)
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
      when(c.preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
        Future.successful(getPaperlessPreferenceResponse)
      }
      when(c.taxCalculationService.getTaxCalculation(meq(nino), meq(year - 1))(any())) thenReturn {
        Future.successful(getTaxCalculationResponse)
      }
      when(c.identityVerificationFrontendService.getIVJourneyStatus(any())(any())) thenReturn {
        Future.successful(getIVJourneyStatusResponse)
      }
      when(c.selfAssessmentService.getSelfAssessmentUserType(any())(any())) thenReturn {
        Future.successful(selfAssessmentUserType)
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

    "return a 200 status when accessing index page with good nino and sa User" in new LocalSetup {

      val userRequest = UserRequestFixture.buildUserRequest()

      val app: Application = localGuiceApplicationBuilder(userRequest)
        .overrides(bind[CitizenDetailsService].toInstance(mock[CitizenDetailsService]))
        .overrides(bind[TaiService].toInstance(mock[TaiService]))
        .overrides(bind[MessageFrontendService].toInstance(mock[MessageFrontendService]))
        //      .overrides(bind[CspPartialService].toInstance(mock[CspPartialService]))
        //      .overrides(bind[PreferencesFrontendService].toInstance(mock[PreferencesFrontendService]))
        //      .overrides(bind[IdentityVerificationFrontendService].toInstance(
        //        mock[IdentityVerificationFrontendService]))
        //      .overrides(bind[PertaxAuthConnector].toInstance(mock[PertaxAuthConnector]))
        .overrides(bind[PertaxAuditConnector].toInstance(mock[PertaxAuditConnector]))
        //      .overrides(bind[FrontEndDelegationConnector].toInstance(mock[FrontEndDelegationConnector]))
        //      .overrides(bind[TaxCalculationService].toInstance(mock[TaxCalculationService]))
        .overrides(bind[UserDetailsService].toInstance(mock[UserDetailsService]))
        //      .overrides(bind[SelfAssessmentService].toInstance(mock[SelfAssessmentService]))
        .overrides(bind[LocalPartialRetriever].toInstance(mock[LocalPartialRetriever]))
        .overrides(bind[ConfigDecorator].toInstance(mock[ConfigDecorator]))
        //      .overrides(bind[LocalSessionCache].toInstance(mock[LocalSessionCache]))
        .build()

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(nino))(any())
      if (controller.configDecorator.taxComponentsEnabled)
        verify(controller.taiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any())
      if (controller.configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
      verify(controller.userDetailsService, times(1)).getUserDetails(meq("/userDetailsLink"))(any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User" in new LocalSetup {

      val userRequest = UserRequestFixture.buildUserRequest(saUser = NonFilerSelfAssessmentUser)

      val app: Application = localGuiceApplicationBuilder(userRequest)
        .overrides(bind[CitizenDetailsService].toInstance(mock[CitizenDetailsService]))
        .overrides(bind[TaiService].toInstance(mock[TaiService]))
        .overrides(bind[MessageFrontendService].toInstance(mock[MessageFrontendService]))
        .overrides(bind[PertaxAuditConnector].toInstance(mock[PertaxAuditConnector]))
        .overrides(bind[UserDetailsService].toInstance(mock[UserDetailsService]))
        .overrides(bind[LocalPartialRetriever].toInstance(mock[LocalPartialRetriever]))
        .overrides(bind[ConfigDecorator].toInstance(mock[ConfigDecorator]))
        .build()

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(nino))(any())
      if (controller.configDecorator.taxComponentsEnabled)
        verify(controller.taiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any())
      if (controller.configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
      verify(controller.userDetailsService, times(1)).getUserDetails(meq("/userDetailsLink"))(any())
    }

    "return 200 when Preferences Frontend returns ActivatePaperlessNotAllowedResponse" in new LocalSetup {

      val userRequest = UserRequestFixture.buildUserRequest()

      val app: Application = localGuiceApplicationBuilder(userRequest)
        .overrides(bind[CitizenDetailsService].toInstance(mock[CitizenDetailsService]))
        .overrides(bind[TaiService].toInstance(mock[TaiService]))
        .overrides(bind[MessageFrontendService].toInstance(mock[MessageFrontendService]))
        .overrides(bind[PertaxAuditConnector].toInstance(mock[PertaxAuditConnector]))
        .overrides(bind[UserDetailsService].toInstance(mock[UserDetailsService]))
        .overrides(bind[LocalPartialRetriever].toInstance(mock[LocalPartialRetriever]))
        .overrides(bind[ConfigDecorator].toInstance(mock[ConfigDecorator]))
        .build()

      override lazy val getPaperlessPreferenceResponse = ActivatePaperlessNotAllowedResponse

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "redirect when Preferences Frontend returns ActivatePaperlessRequiresUserActionResponse" in new LocalSetup {

      val userRequest = UserRequestFixture.buildUserRequest()

      val app: Application = localGuiceApplicationBuilder(userRequest)
        .overrides(bind[CitizenDetailsService].toInstance(mock[CitizenDetailsService]))
        .overrides(bind[TaiService].toInstance(mock[TaiService]))
        .overrides(bind[MessageFrontendService].toInstance(mock[MessageFrontendService]))
        .overrides(bind[PertaxAuditConnector].toInstance(mock[PertaxAuditConnector]))
        .overrides(bind[UserDetailsService].toInstance(mock[UserDetailsService]))
        .overrides(bind[LocalPartialRetriever].toInstance(mock[LocalPartialRetriever]))
        .overrides(bind[ConfigDecorator].toInstance(mock[ConfigDecorator]))
        .build()
      override lazy val getPaperlessPreferenceResponse =
        ActivatePaperlessRequiresUserActionResponse("http://www.example.com")

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("http://www.example.com")
    }

    "return 200 when TaxCalculationService returns TaxCalculationNotFoundResponse" in new LocalSetup {

      val userRequest = UserRequestFixture.buildUserRequest()

      val app: Application = localGuiceApplicationBuilder(userRequest)
        .overrides(bind[CitizenDetailsService].toInstance(mock[CitizenDetailsService]))
        .overrides(bind[TaiService].toInstance(mock[TaiService]))
        .overrides(bind[MessageFrontendService].toInstance(mock[MessageFrontendService]))
        .overrides(bind[PertaxAuditConnector].toInstance(mock[PertaxAuditConnector]))
        .overrides(bind[UserDetailsService].toInstance(mock[UserDetailsService]))
        .overrides(bind[LocalPartialRetriever].toInstance(mock[LocalPartialRetriever]))
        .overrides(bind[ConfigDecorator].toInstance(mock[ConfigDecorator]))
        .build()

      override lazy val getTaxCalculationResponse = TaxCalculationNotFoundResponse

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
      if (controller.configDecorator.taxcalcEnabled)
        verify(controller.taxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return a 200 status when accessing index page with a nino that does not map to any personal details in citizen-details" in new LocalSetup {

      val userRequest = UserRequestFixture.buildUserRequest(personDetails = None)

      val app: Application = localGuiceApplicationBuilder(userRequest)
        .overrides(bind[CitizenDetailsService].toInstance(mock[CitizenDetailsService]))
        .overrides(bind[TaiService].toInstance(mock[TaiService]))
        .overrides(bind[MessageFrontendService].toInstance(mock[MessageFrontendService]))
        .overrides(bind[PertaxAuditConnector].toInstance(mock[PertaxAuditConnector]))
        .overrides(bind[UserDetailsService].toInstance(mock[UserDetailsService]))
        .overrides(bind[LocalPartialRetriever].toInstance(mock[LocalPartialRetriever]))
        .overrides(bind[ConfigDecorator].toInstance(mock[ConfigDecorator]))
        .build()

      val r: Future[Result] = controller.index()(FakeRequest())
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

  }

  "Calling serviceCallResponses" should {

    val userNino = Some(fakeNino)

    "return TaxComponentsDisabled status where there is not a Nino" in new LocalSetup {

      val result = await(controller.serviceCallResponses(None, year))

      result shouldBe ((TaxComponentsDisabledState, None, None))
    }

    "return TaxComponentsDisabled where taxComponents is not enabled" in new LocalSetup {

      when(controller.configDecorator.taxComponentsEnabled) thenReturn false

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result shouldBe TaxComponentsDisabledState

    }

    "return TaxCalculationAvailable status when data returned from TaxCalculation" in new LocalSetup {

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))
      result shouldBe TaxComponentsAvailableState(
        TaxComponents(Seq("EmployerProvidedServices", "PersonalPensionPayments")))

    }

    "return TaxComponentsNotAvailableState status when TaxComponentsUnavailableResponse from TaxComponents" in new LocalSetup {

      when(controller.taiService.taxComponents(any[Nino], any[Int])(any[HeaderCarrier])) thenReturn {
        Future.successful(TaxComponentsUnavailableResponse)
      }

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result shouldBe TaxComponentsNotAvailableState
    }

    "return TaxComponentsUnreachableState status when there is TaxComponents returns an unexpected response" in new LocalSetup {

      when(controller.taiService.taxComponents(any[Nino], any[Int])(any[HeaderCarrier])) thenReturn {
        Future.successful(TaxComponentsUnexpectedResponse(HttpResponse(INTERNAL_SERVER_ERROR)))
      }

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result shouldBe TaxComponentsUnreachableState
    }

    "return None where TaxCalculation service is not enabled" in new LocalSetup {

      when(controller.configDecorator.taxcalcEnabled) thenReturn false

      val (_, resultCYm1, resultCYm2) = await(controller.serviceCallResponses(userNino, year))

      resultCYm1 shouldBe None
      resultCYm2 shouldBe None
    }

    "return only  CY-1 None and CY-2 None when get TaxYearReconcillation returns Nil" in new LocalSetup {

      when(controller.taxCalculationService.getTaxYearReconciliations(any())(any())) thenReturn Future.successful(Nil)

      val (_, resultCYM1, resultCYM2) = await(controller.serviceCallResponses(userNino, year))

      resultCYM1 shouldBe None
      resultCYM2 shouldBe None
    }

    "return taxCalculation for CY1 and CY2 status from list returned from TaxCalculation Service." in new LocalSetup {

      val (_, resultCYM1, resultCYM2) = await(controller.serviceCallResponses(userNino, year))

      resultCYM1 shouldBe Some(TaxYearReconciliation(2016, Balanced))
      resultCYM2 shouldBe Some(TaxYearReconciliation(2015, Balanced))
    }
  }
}
