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

package controllers.auth

import config.ConfigDecorator
import connectors.PertaxAuthConnector
import controllers.auth.requests.AuthenticatedRequest
import models.UserName
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Action, AnyContent, Controller, Session}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, LoginTimes, ~}
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.binders.Origin
import util.Fixtures
import util.RetrievalOps._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.language.postfixOps

class AuthActionSpec extends FreeSpec with MustMatchers with MockitoSugar with OneAppPerSuite with ScalaFutures {

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
    .configure(Map("metrics.enabled" -> false))
    .build()

  val mockAuthConnector: PertaxAuthConnector = mock[PertaxAuthConnector]
  val configDecorator = app.injector.instanceOf[ConfigDecorator]

  class Harness(authAction: AuthAction) extends Controller {
    def onPageLoad(): Action[AnyContent] = authAction { request: AuthenticatedRequest[AnyContent] =>
      Ok(
        s"Nino: ${request.nino.getOrElse("fail").toString}, SaUtr: ${request.saEnrolment.map(_.saUtr).getOrElse("fail").toString}," +
          s"trustedHelper: ${request.trustedHelper}")
    }
  }

  type AuthRetrievals =
    Option[String] ~ Enrolments ~ Option[Credentials] ~ ConfidenceLevel ~ Option[UserName] ~ LoginTimes ~ Option[
      TrustedHelper] ~ Option[String]

  val fakeCredentials = Credentials("foo", "bar")
  val fakeConfidenceLevel = ConfidenceLevel.L200
  val fakeLoginTimes = LoginTimes(DateTime.now(), None)

  def fakeSaEnrolments(utr: String) = Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", utr)), "Activated"))

  "A user without a L200 confidence level must" - {
    "be redirected to the uplift endpoint" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientConfidenceLevel()))
      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration, configDecorator)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad()(FakeRequest("GET", "/personal-account"))
      status(result) mustBe SEE_OTHER
      redirectLocation(result).get must endWith(
        "/mdtp/uplift?origin=PERTAX&confidenceLevel=200&completionURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account&failureURL=%2Fpersonal-account%2Fidentity-check-complete%3FcontinueUrl%3D%252Fpersonal-account")
    }
  }

  "A user with no active session must" - {
    "be redirected to the auth provider choice page if unknown provider" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(SessionRecordNotFound()))
      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration, configDecorator)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad()(FakeRequest("GET", "/foo"))
      status(result) mustBe SEE_OTHER
      redirectLocation(result).get must endWith("/auth-login-stub")
    }
    "be redirected to the IDA login page if Verify provider" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(SessionRecordNotFound()))
      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration, configDecorator)
      val controller = new Harness(authAction)
      val request =
        FakeRequest("GET", "/foo").withSession(SessionKeys.authProvider -> configDecorator.authProviderVerify)
      val result = controller.onPageLoad()(request)
      status(result) mustBe SEE_OTHER
      session(result) mustBe new Session(
        Map(
          "loginOrigin"    -> Origin("PERTAX").origin,
          "login_redirect" -> "/personal-account/do-uplift?redirectUrl=%2Ffoo"))
      redirectLocation(result).get must endWith("/ida/login")
    }
    "be redirected to the GG login page if GG provider" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(SessionRecordNotFound()))
      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration, configDecorator)
      val controller = new Harness(authAction)
      val request =
        FakeRequest("GET", "/foo").withSession(SessionKeys.authProvider -> configDecorator.authProviderGG)
      val result = controller.onPageLoad()(request)
      status(result) mustBe SEE_OTHER

      redirectLocation(result).get must endWith(
        "/gg/sign-in?continue=%2Fpersonal-account%2Fdo-uplift%3FredirectUrl%3D%252Ffoo&accountType=individual&origin=PERTAX")
    }
  }

  "A user with insufficient enrolments must" - {
    "be redirected to the Sorry there is a problem page" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientEnrolments()))
      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration, configDecorator)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad()(FakeRequest("GET", "/foo"))

      whenReady(result.failed) { ex =>
        ex mustBe an[InsufficientEnrolments]
      }
    }
  }

  "A user with nino and no SA enrolment must" - {
    "create an authenticated request" in {

      val nino = Fixtures.fakeNino.nino
      val retrievalResult: Future[AuthRetrievals] =
        Future.successful(
          Some(nino) ~ Enrolments(Set.empty) ~ Some(fakeCredentials) ~ fakeConfidenceLevel ~ None ~ fakeLoginTimes ~ None ~ None
        )

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any()))
        .thenReturn(retrievalResult)

      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration, configDecorator)
      val controller = new Harness(authAction)

      val result = controller.onPageLoad()(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(nino)
    }
  }

  "A user with no nino but an SA enrolment must" - {
    "create an authenticated request" in {

      val utr = new SaUtrGenerator().nextSaUtr.utr
      val retrievalResult: Future[AuthRetrievals] =
        Future.successful(
          None ~ Enrolments(fakeSaEnrolments(utr)) ~ Some(fakeCredentials) ~ fakeConfidenceLevel ~ None ~ fakeLoginTimes ~ None ~ None)

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any()))
        .thenReturn(retrievalResult)

      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration, configDecorator)
      val controller = new Harness(authAction)

      val result = controller.onPageLoad()(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(utr)
    }
  }

  "A user with a nino and an SA enrolment must" - {
    "create an authenticated request" in {

      val nino = Fixtures.fakeNino.nino
      val utr = new SaUtrGenerator().nextSaUtr.utr
      val retrievalResult: Future[AuthRetrievals] =
        Future.successful(
          Some(nino) ~ Enrolments(fakeSaEnrolments(utr)) ~ Some(fakeCredentials) ~ fakeConfidenceLevel ~ None ~ fakeLoginTimes ~ None ~ None
        )

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any()))
        .thenReturn(retrievalResult)

      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration, configDecorator)
      val controller = new Harness(authAction)

      val result = controller.onPageLoad()(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(nino)
      contentAsString(result) must include(utr)
    }
  }

  "A user with trustedHelper must" - {
    "create an authenticated request containing the trustedHelper" in {

      val fakePrincipalNino = Fixtures.fakeNino.toString()
      val retrievalResult: Future[AuthRetrievals] =
        Future.successful(
          Some(Fixtures.fakeNino.toString()) ~ Enrolments(Set.empty) ~ Some(fakeCredentials) ~ fakeConfidenceLevel ~ None ~ fakeLoginTimes ~ Some(
            TrustedHelper("principalName", "attorneyName", "returnUrl", fakePrincipalNino)) ~ None
        )

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any()))
        .thenReturn(retrievalResult)

      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration, configDecorator)
      val controller = new Harness(authAction)

      val result = controller.onPageLoad()(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(
        s"Some(TrustedHelper(principalName,attorneyName,returnUrl,$fakePrincipalNino))")
    }
  }
}
