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

package controllers

import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.mvc.{MessagesControllerComponents, Session}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.renderer.TemplateRenderer
import util.BaseSpec
import util.Fixtures._
import views.html.public.SessionTimeoutView

import scala.concurrent.ExecutionContext

class PublicControllerSpec extends BaseSpec {

  private val mockTemplateRenderer = mock[TemplateRenderer]

  private def controller = new PublicController(injected[MessagesControllerComponents], injected[SessionTimeoutView])(
    mockLocalPartialRetriever,
    config,
    mockTemplateRenderer,
    injected[ExecutionContext]
  )

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

    "redirect to /personal-details page" in {
      val r = controller.redirectToPersonalDetails()(buildFakeRequestWithAuth("GET"))

      status(r) mustBe SEE_OTHER
      redirectLocation(r) mustBe Some("/personal-account/personal-details")
    }
  }

  "Calling PublicController.verifyEntryPoint" must {

    "redirect to /personal-account page with Verify auth provider" in {
      val request = FakeRequest("GET", "/personal-account/start-verify")
      val r = controller.verifyEntryPoint()(request)

      status(r) mustBe SEE_OTHER
      redirectLocation(r) mustBe Some("/personal-account")
      session(r) mustBe new Session(Map(config.authProviderKey -> config.authProviderVerify))
    }
  }

  "Calling PublicController.governmentGatewayEntryPoint" must {

    "redirect to /personal-account page with GG auth provider" in {
      val request = FakeRequest("GET", "/personal-account/start-government-gateway")
      val r = controller.governmentGatewayEntryPoint()(request)

      status(r) mustBe SEE_OTHER
      redirectLocation(r) mustBe Some("/personal-account")
      session(r) mustBe new Session(Map(config.authProviderKey -> config.authProviderGG))
    }
  }
}
