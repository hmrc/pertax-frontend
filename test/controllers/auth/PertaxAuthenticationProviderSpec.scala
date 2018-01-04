/*
 * Copyright 2018 HM Revenue & Customs
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
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.frontend.auth.{AuthenticationProviderIds, UserCredentials}
import util.BaseSpec
import uk.gov.hmrc.http.SessionKeys

class PertaxAuthenticationProviderSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[ConfigDecorator].toInstance(MockitoSugar.mock[ConfigDecorator]))
    .build()

  "Calling PertaxAuthenticationProvider.handleNotAuthenticated" should {

    trait LocalSetup {

      def authProvider: String

      val userCredentials = UserCredentials(None, None)

      lazy val request = FakeRequest("GET", "/").withSession(SessionKeys.authProvider -> authProvider)

      lazy val pertaxAuthenticationProvider = new PertaxAuthenticationProvider(
        injected[ConfigDecorator]
      )

      lazy val partialFunction = {
        val c = injected[PertaxAuthenticationProvider]
        when(c.configDecorator.companyAuthHost) thenReturn  ""
        when(c.configDecorator.citizenAuthHost) thenReturn  ""
        when(c.configDecorator.ida_web_context) thenReturn  "ida"
        when(c.configDecorator.gg_web_context) thenReturn  "gg"
        when(c.configDecorator.pertaxFrontendHost) thenReturn "/something"
        when(c.postSignInRedirectUrl(request)) thenReturn  "/personal-account"

        c.handleNotAuthenticated(request)
      }
    }

    "redirect to gg login page if user the authentication provider session variable is set to Government Gateway"  in new LocalSetup {
      override lazy val authProvider = AuthenticationProviderIds.GovernmentGatewayId
      val r: Result = await(partialFunction(userCredentials)).right.get
      r.header.status shouldBe SEE_OTHER
      r.header.headers("Location") shouldBe "/gg/sign-in?continue=%2Fpersonal-account%2Fpersonal-account%2Fdo-uplift%3FredirectUrl%3D%252Fpersonal-account%252F&accountType=individual&origin=PERTAX"
    }

    "redirect to ida login page if user the authentication provider session variable is set to Verify"  in new LocalSetup {
      override lazy val authProvider = AuthenticationProviderIds.VerifyProviderId
      val r: Result = await(partialFunction(userCredentials)).right.get
      r.header.status shouldBe SEE_OTHER
      session(r).apply("loginOrigin") shouldBe "PERTAX"
      session(r).apply("login_redirect") shouldBe "/personal-account/personal-account/do-uplift?redirectUrl=%2Fpersonal-account%2F"
      r.header.headers("Location") shouldBe "/ida/login"
    }

    "redirect to gg login page if user the authentication provider session variable is invalid"  in new LocalSetup {
      override lazy val authProvider = "Invalid Provider"
      val r: Result = await(partialFunction(userCredentials)).right.get
      r.header.status shouldBe SEE_OTHER
      r.header.headers("Location") shouldBe "/gg/sign-in?continue=%2Fpersonal-account%2Fpersonal-account%2Fdo-uplift%3FredirectUrl%3D%252Fpersonal-account%252F&accountType=individual&origin=PERTAX"
    }
  }
}
