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

package controllers.auth

import controllers.address
import controllers.auth.requests.AuthenticatedRequest
import controllers.bindable.ResidentialAddrType
import models.UserName
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.HeaderNames
import play.api.http.Status.SEE_OTHER
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Individual, Organisation}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.binders.Origin
import util.RetrievalOps._
import util.{BaseSpec, EnrolmentsHelper, Fixtures}

import scala.concurrent.Future

class AuthActionSpec extends BaseSpec {

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
    .configure(Map("metrics.enabled" -> false))
    .build()

  val mockAuthConnector = mock[AuthConnector]
  def controllerComponents: ControllerComponents = app.injector.instanceOf[ControllerComponents]
  val enrolmentsHelper: EnrolmentsHelper = app.injector.instanceOf[EnrolmentsHelper]
  val sessionAuditor =
    new SessionAuditorFake(app.injector.instanceOf[AuditConnector], enrolmentsHelper)

  class Harness(authAction: AuthAction) extends InjectedController {
    def onPageLoad: Action[AnyContent] = authAction { request: AuthenticatedRequest[AnyContent] =>
      Ok(
        s"Nino: ${request.nino.getOrElse("fail").toString}, Enrolments: ${request.enrolments.toString}," +
          s"trustedHelper: ${request.trustedHelper}, profileUrl: ${request.profile}"
      )
    }
  }

  type AuthRetrievals =
    Option[String] ~ Option[AffinityGroup] ~ Enrolments ~ Option[Credentials] ~ Option[
      String
    ] ~ ConfidenceLevel ~ Option[UserName] ~ Option[TrustedHelper] ~ Option[String]

  val nino = Fixtures.fakeNino.nino
  val fakeCredentials = Credentials("foo", "bar")
  val fakeCredentialStrength = CredentialStrength.strong
  val fakeConfidenceLevel = ConfidenceLevel.L200
  val enrolmentHelper = injected[EnrolmentsHelper]

  def fakeEnrolments(utr: String) = Set(
    Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", utr)), "Activated"),
    Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino.toString)), "None", None)
  )

  def retrievals(
    nino: Option[String] = Some(nino.toString),
    affinityGroup: Option[AffinityGroup] = Some(Individual),
    saEnrolments: Enrolments = Enrolments(
      Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino.toString)), "None", None))
    ),
    credentialStrength: String = CredentialStrength.strong,
    confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200,
    trustedHelper: Option[TrustedHelper] = None,
    profileUrl: Option[String] = None
  ): Harness = {

    when(mockAuthConnector.authorise[AuthRetrievals](any(), any())(any(), any())) thenReturn Future.successful(
      nino ~ affinityGroup ~ saEnrolments ~ Some(fakeCredentials) ~ Some(
        credentialStrength
      ) ~ confidenceLevel ~ None ~ trustedHelper ~ profileUrl
    )

    val authAction =
      new AuthActionImpl(mockAuthConnector, config, sessionAuditor, controllerComponents, enrolmentsHelper)

    new Harness(authAction)
  }

  val ivRedirectUrl =
    "http://localhost:9948/iv-stub/uplift?origin=PERTAX&confidenceLevel=200&completionURL=http%3A%2F%2Flocalhost%3A9232%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account&failureURL=http%3A%2F%2Flocalhost%3A9232%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account"

  "A user without a L200 confidence level must" when {
    "be redirected to the IV uplift endpoint when" must {
      "the user is an Individual" in {

        val controller = retrievals(confidenceLevel = ConfidenceLevel.L50)
        val result = controller.onPageLoad(FakeRequest("GET", "/personal-account"))
        status(result) mustBe SEE_OTHER
        redirectLocation(result).get must endWith(ivRedirectUrl)
      }

      "the user is an Organisation" in {

        val controller = retrievals(affinityGroup = Some(Organisation), confidenceLevel = ConfidenceLevel.L50)
        val result = controller.onPageLoad(FakeRequest("GET", "/personal-account"))
        status(result) mustBe SEE_OTHER
        redirectLocation(result).get must endWith(ivRedirectUrl)
      }

      "the user is an Agent" in {

        val controller = retrievals(affinityGroup = Some(Agent), confidenceLevel = ConfidenceLevel.L50)
        val result = controller.onPageLoad(FakeRequest("GET", "/personal-account"))
        status(result) mustBe SEE_OTHER
        redirectLocation(result).get must endWith(ivRedirectUrl)
      }
    }
  }

  "A user without a credential strength of Strong must" when {
    "be redirected to the MFA uplift endpoint when" must {

      val mfaRedirectUrl =
        Some(
          "http://localhost:9553/bas-gateway/uplift-mfa?origin=PERTAX&continueUrl=http%3A%2F%2Flocalhost%3A9232%2Fpersonal-account"
        )

      "the user in an Individual" in {

        val controller = retrievals(credentialStrength = CredentialStrength.weak)
        val result = controller.onPageLoad(FakeRequest("GET", "/personal-account"))
        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe mfaRedirectUrl
      }

      "the user in an Organisation" in {

        val controller = retrievals(affinityGroup = Some(Organisation), credentialStrength = CredentialStrength.weak)
        val result = controller.onPageLoad(FakeRequest("GET", "/personal-account"))
        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe
          mfaRedirectUrl
      }

      "the user in an Agent" in {

        val controller = retrievals(affinityGroup = Some(Agent), credentialStrength = CredentialStrength.weak)
        val result = controller.onPageLoad(FakeRequest("GET", "/personal-account"))
        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe
          mfaRedirectUrl
      }
    }
  }

  "A user with a Credential Strength of 'none' must" must {
    "be redirected to the auth provider choice page" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(IncorrectCredentialStrength()))
      val authAction =
        new AuthActionImpl(mockAuthConnector, config, sessionAuditor, controllerComponents, enrolmentsHelper)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad(FakeRequest("GET", "/foo"))
      status(result) mustBe SEE_OTHER
      redirectLocation(result).get must endWith("/auth-login-stub")
    }
  }

  "A user with no active session must" must {
    "be redirected to the auth provider choice page if unknown provider" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(SessionRecordNotFound()))
      val authAction =
        new AuthActionImpl(mockAuthConnector, config, sessionAuditor, controllerComponents, enrolmentsHelper)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad(FakeRequest("GET", "/foo"))
      status(result) mustBe SEE_OTHER
      redirectLocation(result).get must endWith("/auth-login-stub")
    }

    "be redirected to the IDA login page if Verify provider" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(SessionRecordNotFound()))
      val authAction =
        new AuthActionImpl(mockAuthConnector, config, sessionAuditor, controllerComponents, enrolmentsHelper)
      val controller = new Harness(authAction)
      val request =
        FakeRequest("GET", "/foo").withSession(config.authProviderKey -> config.authProviderVerify)
      val result = controller.onPageLoad(request)
      status(result) mustBe SEE_OTHER
      session(result) mustBe new Session(
        Map(
          "loginOrigin"    -> Origin("PERTAX").origin,
          "login_redirect" -> "http://localhost:9232/personal-account/do-uplift?redirectUrl=http%3A%2F%2Flocalhost%3A9232%2Ffoo"
        )
      )
      redirectLocation(result).get must endWith("/ida/login")
    }

    "be redirected to the GG login page if GG provider" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(SessionRecordNotFound()))
      val authAction =
        new AuthActionImpl(mockAuthConnector, config, sessionAuditor, controllerComponents, enrolmentsHelper)
      val controller = new Harness(authAction)
      val request =
        FakeRequest("GET", "/foo").withSession(config.authProviderKey -> config.authProviderGG)
      val result = controller.onPageLoad(request)
      status(result) mustBe SEE_OTHER

      redirectLocation(result).get must endWith(
        "http://localhost:9553/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9232%2Fpersonal-account%2Fdo-uplift%3FredirectUrl%3Dhttp%253A%252F%252Flocalhost%253A9232%252Ffoo&accountType=individual&origin=PERTAX"
      )
    }
  }

  "A user with insufficient enrolments must" must {
    "be redirected to the Sorry there is a problem page" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientEnrolments()))
      val authAction =
        new AuthActionImpl(mockAuthConnector, config, sessionAuditor, controllerComponents, enrolmentsHelper)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad(FakeRequest("GET", "/foo"))

      whenReady(result.failed) { ex =>
        ex mustBe an[InsufficientEnrolments]
      }
    }
  }

  "A user with nino and no SA enrolment must" must {
    "create an authenticated request" in {

      val controller = retrievals()

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(nino)
    }
  }

  "A user with no nino but an SA enrolment must" must {
    "create an authenticated request" in {

      val utr = new SaUtrGenerator().nextSaUtr.utr

      val controller = retrievals(nino = None, saEnrolments = Enrolments(fakeEnrolments(utr)))

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(utr)
    }
  }

  "A user with a nino and an SA enrolment must" must {
    "create an authenticated request" in {

      val utr = new SaUtrGenerator().nextSaUtr.utr

      val controller = retrievals(saEnrolments = Enrolments(fakeEnrolments(utr)))

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(nino)
      contentAsString(result) must include(utr)
    }
  }

  "A user with trustedHelper must" must {
    "create an authenticated request containing the trustedHelper" in {

      val fakePrincipalNino = Fixtures.fakeNino.toString()

      val controller =
        retrievals(trustedHelper = Some(TrustedHelper("principalName", "attorneyName", "returnUrl", fakePrincipalNino)))

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(
        s"Some(TrustedHelper(principalName,attorneyName,returnUrl,$fakePrincipalNino))"
      )
    }
  }

  "A user with a SCP Profile Url must include a redirect uri back to the home controller" in {
    val controller = retrievals(profileUrl = Some("http://www.google.com/"))

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) must include(s"http://www.google.com/?redirect_uri=${config.pertaxFrontendBackLink}")
  }

  "A user with a no SingleAccount enrolment should redirect" in {

    when(mockAuthConnector.authorise[AuthRetrievals](any(), any())(any(), any())) thenReturn Future.successful(
      Some(nino) ~
        Some(Individual) ~
        Enrolments(Set.empty) ~
        Some(fakeCredentials) ~
        Some(CredentialStrength.strong) ~
        ConfidenceLevel.L200 ~
        None ~
        None ~
        None
    )

    val authAction =
      new AuthActionImpl(mockAuthConnector, config, sessionAuditor, controllerComponents, enrolmentsHelper)

    val controller = new Harness(authAction)

    val result = controller.onPageLoad(
      FakeRequest(
        method = "GET",
        uri = "https://example.com",
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsEmpty
      )
    )

    status(result) mustBe SEE_OTHER
    redirectLocation(result) mustBe Some("http://localhost:7750/protect-tax-info?redirectUrl=Some(https://example.com)")
  }

  "A user without a SCP Profile Url must continue to not have one" in {
    val controller = retrievals(profileUrl = None)

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) mustNot include(s"http://www.google.com/?redirect_uri=${config.pertaxFrontendBackLink}")
  }

  "A user with a SCP Profile Url that is not valid must strip out the SCP Profile Url" in {
    val controller = retrievals(profileUrl = Some("notAUrl"))

    val result = controller.onPageLoad(FakeRequest("", ""))
    status(result) mustBe OK
    contentAsString(result) mustNot include(config.pertaxFrontendBackLink)
  }

  "A user that has logged in with Verify must" must {
    "create an authenticated request" in {

      val controller = retrievals(
        credentialStrength = CredentialStrength.strong,
        confidenceLevel = ConfidenceLevel.L500,
        affinityGroup = None
      )

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(nino)
    }
  }
}
