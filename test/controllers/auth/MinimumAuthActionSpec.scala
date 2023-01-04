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

package controllers.auth

import controllers.auth.requests.AuthenticatedRequest
import models.UserName
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import testUtils.RetrievalOps.Ops
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.EnrolmentsHelper

import scala.concurrent.Future
import scala.language.postfixOps

class MinimumAuthActionSpec extends BaseSpec {

  override implicit lazy val app: Application    = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
    .configure(Map("metrics.enabled" -> false))
    .build()
  type AuthRetrievals =
    Option[String] ~ Option[AffinityGroup] ~ Enrolments ~ Option[Credentials] ~ ConfidenceLevel ~ Option[
      UserName
    ] ~ Option[
      TrustedHelper
    ] ~ Option[String]
  val cc: ControllerComponents                   = stubControllerComponents()
  val mockAuthConnector: AuthConnector           = mock[AuthConnector]
  val controllerComponents: ControllerComponents = app.injector.instanceOf[ControllerComponents]
  val sessionAuditor                             =
    new SessionAuditorFake(app.injector.instanceOf[AuditConnector], app.injector.instanceOf[EnrolmentsHelper])
  val enrolmentsHelper: EnrolmentsHelper         = injected[EnrolmentsHelper]

  "A user with no active session must" must {
    "be redirected to the session timeout page" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(SessionRecordNotFound()))
      val authAction =
        new MinimumAuthAction(
          mockAuthConnector,
          app.configuration,
          config,
          sessionAuditor,
          controllerComponents,
          enrolmentsHelper
        )
      val controller = new Harness(authAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/foo"))
      status(result) mustBe SEE_OTHER
      redirectLocation(result).get must endWith("/personal-account/signin")
    }
  }

  "A user with insufficient enrolments must" must {
    "be redirected to the Sorry there is a problem page" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientEnrolments()))
      val authAction =
        new MinimumAuthAction(
          mockAuthConnector,
          app.configuration,
          config,
          sessionAuditor,
          controllerComponents,
          enrolmentsHelper
        )
      val controller = new Harness(authAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/foo"))

      whenReady(result.failed) { ex =>
        ex mustBe an[InsufficientEnrolments]
      }
    }
  }
  val fakeCredentials: Credentials = Credentials("foo", "bar")
  val fakeConfidenceLevel: ConfidenceLevel = ConfidenceLevel.L200

  def fakeSaEnrolments(utr: String) = Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", utr)), "Activated"))

  class Harness(authAction: MinimumAuthAction) extends AbstractController(cc) {
    def onPageLoad: Action[AnyContent] = authAction { request: AuthenticatedRequest[AnyContent] =>
      Ok(
        s"Nino: ${request.nino.getOrElse("fail").toString}, Enrolments: ${request.enrolments.toString}," +
          s"trustedHelper: ${request.trustedHelper}"
      )
    }
  }

  "A user with nino and no SA enrolment must" must {
    "create an authenticated request" in {

      val nino                                    = Fixtures.fakeNino.nino
      val retrievalResult: Future[AuthRetrievals] =
        Future.successful(
          Some(nino) ~ None ~ Enrolments(Set.empty) ~ Some(fakeCredentials) ~ fakeConfidenceLevel ~ None ~ None ~ None
        )

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any())
      ).thenReturn(retrievalResult)

      val authAction =
        new MinimumAuthAction(
          mockAuthConnector,
          app.configuration,
          config,
          sessionAuditor,
          controllerComponents,
          enrolmentsHelper
        )
      val controller = new Harness(authAction)

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(nino)
    }
  }

  "A user with no nino but an SA enrolment must" must {
    "create an authenticated request" in {

      val utr = new SaUtrGenerator().nextSaUtr.utr

      val retrievalResult: Future[AuthRetrievals] =
        Future.successful(
          None ~ None ~ Enrolments(fakeSaEnrolments(utr)) ~ Some(
            fakeCredentials
          ) ~ fakeConfidenceLevel ~ None ~ None ~ None
        )

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any())
      ).thenReturn(retrievalResult)

      val authAction =
        new MinimumAuthAction(
          mockAuthConnector,
          app.configuration,
          config,
          sessionAuditor,
          controllerComponents,
          enrolmentsHelper
        )
      val controller = new Harness(authAction)

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(utr)
    }
  }

  "A user with a nino and an SA enrolment must" must {
    "create an authenticated request" in {

      val nino = Fixtures.fakeNino.nino
      val utr  = new SaUtrGenerator().nextSaUtr.utr

      val retrievalResult: Future[AuthRetrievals] =
        Future.successful(
          Some(nino) ~ None ~ Enrolments(fakeSaEnrolments(utr)) ~ Some(
            fakeCredentials
          ) ~ fakeConfidenceLevel ~ None ~ None ~ None
        )

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any())
      ).thenReturn(retrievalResult)

      val authAction =
        new MinimumAuthAction(
          mockAuthConnector,
          app.configuration,
          config,
          sessionAuditor,
          controllerComponents,
          enrolmentsHelper
        )
      val controller = new Harness(authAction)

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(nino)
      contentAsString(result) must include(utr)
    }
  }

  "A user with trustedHelper must" must {
    "create an authenticated request containing the trustedHelper" in {

      val fakePrincipalNino                       = Fixtures.fakeNino.toString()
      val retrievalResult: Future[AuthRetrievals] =
        Future.successful(
          Some(Fixtures.fakeNino.toString()) ~ None ~ Enrolments(Set.empty) ~ Some(
            fakeCredentials
          ) ~ fakeConfidenceLevel ~ None ~ Some(
            TrustedHelper("principalName", "attorneyName", "returnUrl", fakePrincipalNino)
          ) ~ None
        )

      when(
        mockAuthConnector
          .authorise[AuthRetrievals](any(), any())(any(), any())
      ).thenReturn(retrievalResult)

      val authAction = new MinimumAuthAction(
        mockAuthConnector,
        app.configuration,
        config,
        sessionAuditor,
        controllerComponents,
        enrolmentsHelper
      )
      val controller = new Harness(authAction)

      val result = controller.onPageLoad(FakeRequest("", ""))
      status(result) mustBe OK
      contentAsString(result) must include(
        s"Some(TrustedHelper(principalName,attorneyName,returnUrl,$fakePrincipalNino))"
      )
    }
  }
}
