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

import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, UserDetails}
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.partials.{MessageFrontendService, PreferencesFrontendPartialService}
import services.{CitizenDetailsService, PreferencesFrontendService, UserDetailsService}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.filters.{CookieCryptoFilter, SessionCookieCryptoFilter}
import uk.gov.hmrc.play.partials.HtmlPartial
import util.Fixtures._
import util.{BaseSpec, LocalPartialRetriever}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PaperlessPreferencesControllerSpec extends BaseSpec {

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

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
    .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
    .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
    .overrides(bind[PreferencesFrontendService].toInstance(MockitoSugar.mock[PreferencesFrontendService]))
    .overrides(bind[PreferencesFrontendPartialService].toInstance(MockitoSugar.mock[PreferencesFrontendPartialService]))
    .overrides(bind[UserDetailsService].toInstance(MockitoSugar.mock[UserDetailsService]))
    .overrides(bind[LocalPartialRetriever].toInstance(MockitoSugar.mock[LocalPartialRetriever]))
    .overrides(bind[MessageFrontendService].toInstance(MockitoSugar.mock[MessageFrontendService]))
    .build()

  override def beforeEach: Unit =
    reset(injected[PreferencesFrontendPartialService])

  trait LocalSetup {

    def withPaye: Boolean
    def withSa: Boolean
    def confidenceLevel: ConfidenceLevel

    lazy val request = buildFakeRequestWithAuth("GET")
    lazy val verifyRequest = buildFakeRequestWithVerify("GET")

    lazy val controller = {

      val c = injected[PaperlessPreferencesController]

      when(c.preferencesFrontendPartialService.getManagePreferencesPartial(any(), any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }
      when(c.userDetailsService.getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(UserDetails.GovernmentGatewayAuthProvider)))
      }
      when(injected[MessageFrontendService].getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }

      c
    }

    lazy val verifyController = {

      val c = injected[PaperlessPreferencesController]

      when(c.preferencesFrontendPartialService.getManagePreferencesPartial(any(), any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }
      when(c.userDetailsService.getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(UserDetails.VerifyAuthProvider)))
      }
      when(injected[MessageFrontendService].getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }

      c
    }
  }

  "Calling PaperlessPreferencesController.managePreferences" should {

    "call getManagePreferences" should {

      "Return 200 and show messages when a user is logged in using GG" in new LocalSetup {

        lazy val withPaye = false
        lazy val withSa = false
        lazy val confidenceLevel = ConfidenceLevel.L200

        val r = controller.managePreferences(request)
        status(r) shouldBe OK
        verify(controller.preferencesFrontendPartialService, times(1)).getManagePreferencesPartial(any(), any())(any())
      }

      "Return 400 for Verify users" in new LocalSetup {

        lazy val withPaye = false
        lazy val withSa = true
        lazy val confidenceLevel = ConfidenceLevel.L500

        val r = verifyController.managePreferences(verifyRequest)
        status(r) shouldBe BAD_REQUEST
      }
    }
  }
}
