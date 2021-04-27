/*
 * Copyright 2021 HM Revenue & Customs
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
import controllers.auth.requests.AuthenticatedRequest
import models.UserName
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Action, AnyContent, Controller, ControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{ConfidenceLevel, _}
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.Fixtures
import util.RetrievalOps._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.language.postfixOps

class MinimumAuthActionSpec
    extends FreeSpec with MustMatchers with MockitoSugar with GuiceOneAppPerSuite with ScalaFutures {

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
    .configure(Map("metrics.enabled" -> false))
    .build()

  val mockAuthConnector = mock[AuthConnector]
  val configDecorator = app.injector.instanceOf[ConfigDecorator]
  val controllerComponents = app.injector.instanceOf[ControllerComponents]
  val sessionAuditor = new SessionAuditorFake(app.injector.instanceOf[AuditConnector])

  class Harness(authAction: MinimumAuthAction) extends Controller {
    def onPageLoad(): Action[AnyContent] = authAction { request: AuthenticatedRequest[AnyContent] =>
      Ok(
        s"Nino: ${request.nino.getOrElse("fail").toString}, SaUtr: ${request.saEnrolment.map(_.saUtr).getOrElse("fail").toString}," +
          s"trustedHelper: ${request.trustedHelper}")
    }
  }

  "A user with no active session must" - {
    "be redirected to the session timeout page" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(SessionRecordNotFound()))
      val authAction = new MinimumAuthAction(
        mockAuthConnector,
        app.configuration,
        configDecorator,
        sessionAuditor,
        controllerComponents)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad()(FakeRequest("GET", "/foo"))
      status(result) mustBe SEE_OTHER
      redirectLocation(result).get must endWith("/personal-account/signin")
    }
  }

  "A user with insufficient enrolments must" - {
    "be redirected to the Sorry there is a problem page" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientEnrolments()))
      val authAction = new MinimumAuthAction(
        mockAuthConnector,
        app.configuration,
        configDecorator,
        sessionAuditor,
        controllerComponents)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad()(FakeRequest("GET", "/foo"))

      whenReady(result.failed) { ex =>
        ex mustBe an[InsufficientEnrolments]
      }
    }
  }

  type AuthRetrievals =
    Option[String] ~ Enrolments ~ Option[Credentials] ~ ConfidenceLevel ~ Option[UserName] ~ Option[TrustedHelper] ~ Option[
      String]

  val fakeCredentials = Credentials("foo", "bar")
  val fakeConfidenceLevel = ConfidenceLevel.L200

  def fakeSaEnrolments(utr: String) = Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", utr)), "Activated"))

  "A user with nino and no SA enrolment must" - {
    "create an authenticated request" in {

      val nino = Fixtures.fakeNino.nino
      val retrievalResult: Future[AuthRetrievals] =
        Future.successful(
          Some(nino) ~ Enrolments(Set.empty) ~ Some(fakeCredentials) ~ fakeConfidenceLevel ~ None ~ None ~ None)

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any()))
        .thenReturn(retrievalResult)

      val authAction = new MinimumAuthAction(
        mockAuthConnector,
        app.configuration,
        configDecorator,
        sessionAuditor,
        controllerComponents)
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
          None ~ Enrolments(fakeSaEnrolments(utr)) ~ Some(fakeCredentials) ~ fakeConfidenceLevel ~ None ~ None ~ None
        )

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any()))
        .thenReturn(retrievalResult)

      val authAction = new MinimumAuthAction(
        mockAuthConnector,
        app.configuration,
        configDecorator,
        sessionAuditor,
        controllerComponents)
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
          Some(nino) ~ Enrolments(fakeSaEnrolments(utr)) ~ Some(fakeCredentials) ~ fakeConfidenceLevel ~ None ~ None ~ None
        )

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any()))
        .thenReturn(retrievalResult)

      val authAction = new MinimumAuthAction(
        mockAuthConnector,
        app.configuration,
        configDecorator,
        sessionAuditor,
        controllerComponents)
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
          Some(Fixtures.fakeNino.toString()) ~ Enrolments(Set.empty) ~ Some(fakeCredentials) ~ fakeConfidenceLevel ~ None ~ Some(
            TrustedHelper("principalName", "attorneyName", "returnUrl", fakePrincipalNino)) ~ None)

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any()))
        .thenReturn(retrievalResult)

      val authAction = new MinimumAuthAction(
        mockAuthConnector,
        app.configuration,
        configDecorator,
        sessionAuditor,
        controllerComponents
      )
      val controller = new Harness(authAction)

      val result = controller.onPageLoad()(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(
        s"Some(TrustedHelper(principalName,attorneyName,returnUrl,$fakePrincipalNino))")
    }
  }
}
