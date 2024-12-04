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

package controllers

import controllers.bindable.Origin
import play.api.mvc.Session
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.BaseSpec
import testUtils.Fixtures._

class PublicControllerSpec extends BaseSpec {
  private lazy val controller: PublicController = app.injector.instanceOf[PublicController]

  "Calling PublicController.sessionTimeout" must {
    "return 200" in {
      val r = controller.sessionTimeout(buildFakeRequestWithAuth("GET"))
      status(r) mustBe OK
    }
  }

  "Calling PublicController.redirectToExitSurvey" must {
    "return 303" in {
      val r = controller.redirectToExitSurvey(Origin("PERTAX"))(buildFakeRequestWithAuth("GET"))
      status(r) mustBe SEE_OTHER
      redirectLocation(r) mustBe Some("http://localhost:9514/feedback/PERTAX")
    }
  }

  "Calling PublicController.redirectToTaxCreditsService" must {
    "redirect to tax-credits-service/renewals/service-router" in {
      val r = controller.redirectToTaxCreditsService()(buildFakeRequestWithAuth("GET"))
      status(r) mustBe MOVED_PERMANENTLY
      redirectLocation(r) mustBe Some("http://localhost:9362/tax-credits-service/renewals/service-router")
    }
  }

  "Calling PublicController.redirectToPersonalDetails" must {
    "redirect to /profile-and-settings page" in {
      val r = controller.redirectToYourProfile()(buildFakeRequestWithAuth("GET"))

      status(r) mustBe SEE_OTHER
      redirectLocation(r) mustBe Some("/personal-account/profile-and-settings")
    }
  }

  "Calling PublicController.governmentGatewayEntryPoint" must {
    "redirect to /personal-account page with GG auth provider" in {
      val r = controller.governmentGatewayEntryPoint()(FakeRequest("GET", "/personal-account/start-government-gateway"))

      status(r) mustBe SEE_OTHER
      redirectLocation(r) mustBe Some("/personal-account")
      session(r) mustBe new Session(Map(config.authProviderKey -> config.authProviderGG))
    }
  }
}
