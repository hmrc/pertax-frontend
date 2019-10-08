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
import connectors.{FrontEndDelegationConnector, PayApiConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.requests.UserRequest
import models._
import org.joda.time.DateTime
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
import services.partials.{CspPartialService, MessageFrontendService}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.binders.Origin

import uk.gov.hmrc.play.frontend.binders.SafeRedirectUrl
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.time.CurrentTaxYear
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.Future

class ApplicationControllerSpec extends BaseSpec with CurrentTaxYear with MockitoSugar {

  lazy val fakeRequest = FakeRequest("", "")
  lazy val userRequest = UserRequest(
    None,
    None,
    None,
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
    "SomeAuth",
    ConfidenceLevel.L200,
    None,
    None,
    None,
    None,
    fakeRequest)

  val mockConfigDecorator = mock[ConfigDecorator]
  val mockAuditConnector = mock[PertaxAuditConnector]

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(
      bind[CitizenDetailsService].toInstance(mock[CitizenDetailsService]),
      bind[MessageFrontendService].toInstance(mock[MessageFrontendService]),
      bind[CspPartialService].toInstance(mock[CspPartialService]),
      bind[IdentityVerificationFrontendService].toInstance(mock[IdentityVerificationFrontendService]),
      bind[PertaxAuthConnector].toInstance(mock[PertaxAuthConnector]),
      bind[PertaxAuditConnector].toInstance(mock[PertaxAuditConnector]),
      bind[FrontEndDelegationConnector].toInstance(mock[FrontEndDelegationConnector]),
      bind[UserDetailsService].toInstance(mock[UserDetailsService]),
      bind[SelfAssessmentService].toInstance(mock[SelfAssessmentService]),
      bind[LocalPartialRetriever].toInstance(mock[LocalPartialRetriever]),
      bind[ConfigDecorator].toInstance(mock[ConfigDecorator]),
      bind[LocalSessionCache].toInstance(mock[LocalSessionCache]),
      bind[PayApiConnector].toInstance(mock[PayApiConnector])
    )
    .build()

  override def beforeEach: Unit =
    reset(
      injected[PertaxAuditConnector],
      injected[PertaxAuthConnector],
      injected[CitizenDetailsService],
      injected[MessageFrontendService],
      injected[UserDetailsService]
    )

  trait LocalSetup {

    lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider
    lazy val nino: Nino = Fixtures.fakeNino
    lazy val personDetailsResponse: PersonDetailsResponse = PersonDetailsSuccessResponse(Fixtures.buildPersonDetails)
    lazy val confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200
    lazy val withPaye: Boolean = true
    lazy val year = current.currentYear
    lazy val getIVJourneyStatusResponse: IdentityVerificationResponse = IdentityVerificationSuccessResponse("Success")
    lazy val getCitizenDetailsResponse = true
    lazy val getSelfAssessmentServiceResponse: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(
      SaUtr("1111111111"))

    val allowLowConfidenceSA = false

    lazy val controller = {

      val c = injected[ApplicationController]

      when(c.userDetailsService.getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(authProviderType)))
      }
      when(c.citizenDetailsService.personDetails(meq(nino))(any())) thenReturn {
        Future.successful(personDetailsResponse)
      }
      when(c.cspPartialService.webchatClickToChatScriptPartial(any())(any())) thenReturn {
        Future.successful(HtmlPartial.Success(None, Html("<script></script>")))
      }

      when(c.identityVerificationFrontendService.getIVJourneyStatus(any())(any())) thenReturn {
        Future.successful(getIVJourneyStatusResponse)
      }
      when(c.selfAssessmentService.getSelfAssessmentUserType(any())(any())) thenReturn {
        Future.successful(getSelfAssessmentServiceResponse)
      }
      when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
        Future.successful(AuditResult.Success)
      }
      when(injected[LocalSessionCache].fetch()(any(), any())) thenReturn {
        Future.successful(Some(CacheMap("id", Map("urBannerDismissed" -> JsBoolean(true)))))
      }
      when(injected[MessageFrontendService].getUnreadMessageCount(any())) thenReturn {
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

      c
    }

    def routeWrapper[T](req: FakeRequest[AnyContentAsEmpty.type]) = {
      controller //Call to inject mocks
      route(app, req)
    }

  }

  "Calling ApplicationController.uplift" should {

    "send the user to IV using the PERTAX origin" in new LocalSetup {

      override lazy val confidenceLevel = ConfidenceLevel.L0
      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser

      val r = controller.uplift(Some(SafeRedirectUrl("/personal-account")))(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe 303
      redirectLocation(r) shouldBe Some(
        "/mdtp/uplift?origin=PERTAX&confidenceLevel=200&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account")

      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }

    "return BAD_REQUEST status when completionURL is not relative" in new LocalSetup {
      override lazy val confidenceLevel = ConfidenceLevel.L0
      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser

      val r =
        routeWrapper(buildFakeRequestWithAuth("GET", "/personal-account/do-uplift?redirectUrl=http://example.com")).get

      status(r) shouldBe BAD_REQUEST
      redirectLocation(r) shouldBe None

      verify(controller.citizenDetailsService, times(0)).personDetails(any())(any())
    }
  }

  "Calling ApplicationController.handleSelfAssessment" should {

    "return 303 when called with a GG user that needs to activate their SA enollment." in new LocalSetup {

      override lazy val getCitizenDetailsResponse = true
      override lazy val getSelfAssessmentServiceResponse =
        NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      val r = controller.handleSelfAssessment()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some(
        "/ssoout/non-digital?continue=%2Fservice%2Fself-assessment%3Faction=activate&step=enteractivationpin")

    }

    "return 200 when called with a GG user that is SA or has an SA enrollment in another account." in new LocalSetup {

      override lazy val getCitizenDetailsResponse = true
      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      val r = controller.handleSelfAssessment()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }
  }

  "Calling ApplicationController.showUpliftJourneyOutcome" should {

    "return 200 when IV journey outcome was Success" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Success")

      val r = controller.showUpliftJourneyOutcome(Some(SafeRedirectUrl("/relative/url")))(
        buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "redirect to the IV exempt landing page when the 'sa allow low confidence' feature is on" in new LocalSetup {

      override val allowLowConfidenceSA = true
      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account/sa-continue")
    }

    "return 401 when IV journey outcome was LockedOut" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("LockedOut")

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe UNAUTHORIZED

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "redirect to the IV exempt landing page when IV journey outcome was InsufficientEvidence" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("InsufficientEvidence")

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account/sa-continue")
    }

    "return 401 when IV journey outcome was UserAborted" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("UserAborted")

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe UNAUTHORIZED

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "return 500 when IV journey outcome was TechnicalIssues" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("TechnicalIssues")

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe INTERNAL_SERVER_ERROR

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "return 500 when IV journey outcome was Timeout" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Timeout")

      val r = controller.showUpliftJourneyOutcome(None)(buildFakeRequestWithAuth("GET", "/?journeyId=XXXXX"))
      status(r) shouldBe INTERNAL_SERVER_ERROR

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "return bad request when continueUrl is not relative" in new LocalSetup {

      override lazy val getIVJourneyStatusResponse = IdentityVerificationSuccessResponse("Success")

      val r = routeWrapper(
        buildFakeRequestWithAuth(
          "GET",
          "/personal-account/identity-check-complete?continueUrl=http://example.com&journeyId=XXXXX")).get

      status(r) shouldBe BAD_REQUEST

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

  }

  "Calling ApplicationController.signout" should {

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with a continue URL and no origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider

      val r = controller.signout(Some(SafeRedirectUrl("/personal-account")), None)(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/gg/sign-out?continue=/personal-account")
    }

    "redirect to verify sign-out link with correct continue url when signed in with verify, a continue URL and no origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.VerifyAuthProvider

      val r = controller.signout(Some(SafeRedirectUrl("/personal-account")), None)(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/ida/signout")
      session(r).get("postLogoutPage") shouldBe Some("/personal-account")
    }

    "redirect to government gateway sign-out link with correct continue url when signed in with government gateway with no continue URL but an origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider

      val r = controller.signout(None, Some(Origin("PERTAX")))(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/gg/sign-out?continue=/feedback/PERTAX")
    }

    "return BAD_REQUEST when signed in with government gateway with no continue URL and no origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.GovernmentGatewayAuthProvider

      val r = controller.signout(None, None)(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe BAD_REQUEST

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "redirect to verify sign-out link with correct continue url when signed in with verify, with no continue URL and but an origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.VerifyAuthProvider

      val r = controller.signout(None, Some(Origin("PERTAX")))(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/ida/signout")
      session(r).get("postLogoutPage") shouldBe Some("/feedback/PERTAX")
    }

    "return 'Bad Request' when supplied no continue URL and no origin" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.VerifyAuthProvider

      val r = controller.signout(None, None)(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe BAD_REQUEST

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }

    "return bad request when supplied with a none relative url" in new LocalSetup {

      override lazy val authProviderType: String = UserDetails.VerifyAuthProvider

      val r = routeWrapper(
        buildFakeRequestWithAuth("GET", "/personal-account/signout?continueUrl=http://example.com&origin=PERTAX")).get
      status(r) shouldBe BAD_REQUEST

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }
  }

  "Calling ApplicationController.ivExemptLandingPage" should {

    "return 200 for a user who has logged in with GG linked and has a full SA enrollment" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      val r = controller.ivExemptLandingPage(None)(buildFakeRequestWithAuth("GET"))

      val doc = Jsoup.parse(contentAsString(r))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1))
        .sendEvent(eventCaptor.capture())(any(), any()) //TODO - check captured event
    }

    "redirect to the SA activation page on the portal for a user logged in with GG linked to SA which is not yet activated" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse =
        NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      val r = controller.ivExemptLandingPage(None)(buildFakeRequestWithAuth("GET"))
      val doc = Jsoup.parse(contentAsString(r))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())

      doc
        .getElementsByClass("heading-large")
        .toString()
        .contains("Activate your Self Assessment registration") shouldBe true
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1))
        .sendEvent(eventCaptor.capture())(any(), any()) //TODO - check captured event
    }

    "redirect to 'Find out how to access your Self Assessment' page for a user who has a SAUtr but logged into the wrong GG account" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      val r = controller.ivExemptLandingPage(None)(buildFakeRequestWithAuth("GET"))
      val doc = Jsoup.parse(contentAsString(r))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())

      doc
        .getElementsByClass("heading-xlarge")
        .toString()
        .contains("Find out how to access your Self Assessment") shouldBe true
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1))
        .sendEvent(eventCaptor.capture())(any(), any()) //TODO - check captured event
    }

    "redirect to 'We cannot confirm your identity' page for a user who has no SAUTR" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser

      val r = controller.ivExemptLandingPage(None)(buildFakeRequestWithAuth("GET"))
      val doc = Jsoup.parse(contentAsString(r))
      status(r) shouldBe OK

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())

      doc.getElementsByClass("heading-xlarge").toString().contains("We cannot confirm your identity") shouldBe true
      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any()) //TODO - check captured event

    }

    "return bad request when continueUrl is not relative" in new LocalSetup {

      override lazy val getSelfAssessmentServiceResponse = NonFilerSelfAssessmentUser

      val r = routeWrapper(
        buildFakeRequestWithAuth("GET", "/personal-account/sa-continue?continueUrl=http://example.com")).get

      status(r) shouldBe BAD_REQUEST

      verify(controller.messageFrontendService, times(1)).getUnreadMessageCount(any())
    }
  }

  override def now: () => DateTime = DateTime.now
}
