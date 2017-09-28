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

import config.LocalTemplateRenderer
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import models.UserDetails
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.twirl.api.Html
import services.partials.PreferencesFrontendPartialService
import services.{CitizenDetailsService, PreferencesFrontendService, UserDetailsService}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.partials.HtmlPartial
import util.Fixtures._
import util.{BaseSpec, LocalPartialRetriever, MockTemplateRenderer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PaperlessPreferencesControllerSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
    .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
    .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
    .overrides(bind[PreferencesFrontendService].toInstance(MockitoSugar.mock[PreferencesFrontendService]))
    .overrides(bind[PreferencesFrontendPartialService].toInstance(MockitoSugar.mock[PreferencesFrontendPartialService]))
    .overrides(bind[UserDetailsService].toInstance(MockitoSugar.mock[UserDetailsService]))
    .overrides(bind[LocalPartialRetriever].toInstance(MockitoSugar.mock[LocalPartialRetriever]))
    .build()


  override def beforeEach: Unit = {
    reset(injected[PreferencesFrontendPartialService])
  }

  trait LocalSetup {

    def withPaye: Boolean
    def withSa: Boolean
    def confidenceLevel: ConfidenceLevel

    lazy val request = buildFakeRequestWithAuth("GET")
    lazy val authority = buildFakeAuthority(withPaye, withSa, confidenceLevel)

    lazy val controller =  {

      val c = injected[PaperlessPreferencesController]

      when(c.authConnector.currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(authority))
      }
      when(c.preferencesFrontendPartialService.getManagePreferencesPartial(any(), any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }
      when(c.userDetailsService.getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(UserDetails.GovernmentGatewayAuthProvider)))
      }

      c
    }
  }

  "Calling PaperlessPreferencesController.managePreferences" should {

    "call getManagePreferences should return 200" in new LocalSetup {

      lazy val withPaye = false
      lazy val withSa = false
      lazy val confidenceLevel = ConfidenceLevel.L200

      val r = controller.managePreferences(request)
      status(r) shouldBe OK
      verify(controller.preferencesFrontendPartialService, times(1)).getManagePreferencesPartial(any(), any())(any())
    }
  }
}
