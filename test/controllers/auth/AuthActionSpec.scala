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

package uk.gov.hmrc.nisp.controllers.auth

import connectors.NewPertaxAuthConnector
import controllers.auth.{AuthAction, AuthActionImpl}
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
import play.api.mvc.{Action, AnyContent, Controller}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientConfidenceLevel, InsufficientEnrolments, SessionRecordNotFound}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.language.postfixOps

class AuthActionSpec extends FreeSpec with MustMatchers with MockitoSugar with OneAppPerSuite with ScalaFutures {

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[AuthConnector].toInstance(mockAuthConnector))
    .build()

  val mockAuthConnector: NewPertaxAuthConnector = mock[NewPertaxAuthConnector]

  class Harness(authAction: AuthAction) extends Controller {
    def onPageLoad(): Action[AnyContent] = authAction { _ =>
      Ok
    }
  }

  "A user without a L200 confidence level must" - {
    "be redirected to the uplift endpoint" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientConfidenceLevel()))
      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad()(FakeRequest("GET", "/foo"))
      status(result) mustBe SEE_OTHER
      redirectLocation(result).get must endWith("/personal-account/do-uplift?redirectUrl=%2Ffoo")
    }
  }

  "A user with no active session must" - {
    "be redirected to the session timeout page" in {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(SessionRecordNotFound()))
      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration)
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
      val authAction = new AuthActionImpl(mockAuthConnector, app.configuration)
      val controller = new Harness(authAction)
      val result = controller.onPageLoad()(FakeRequest("GET", "/foo"))

      whenReady(result.failed) { ex =>
        ex mustBe an[InsufficientEnrolments]
      }
    }
  }

}
