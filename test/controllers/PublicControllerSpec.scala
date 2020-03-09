/*
 * Copyright 2020 HM Revenue & Customs
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

import config.ConfigDecorator
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{MessagesControllerComponents, Session}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.renderer.TemplateRenderer
import util.BaseSpec
import util.Fixtures._

import scala.concurrent.ExecutionContext

class PublicControllerSpec extends BaseSpec with MockitoSugar {

  private val mockTemplateRenderer = mock[TemplateRenderer]
  private val configDecorator = injected[ConfigDecorator]

  private def controller = new PublicController(injected[MessagesControllerComponents])(
    mockLocalPartialRetriever,
    configDecorator,
    mockTemplateRenderer,
    injected[ExecutionContext]
  )

  "Calling PublicController.sessionTimeout" should {

    "return 200" in {

      val r = controller.sessionTimeout(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK
    }
  }

  "Calling PublicController.redirectToExitSurvey" should {

    "return 303" in {

      val r = controller.redirectToExitSurvey(Origin("PERTAX"))(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/feedback/PERTAX")
    }
  }

  "Calling PublicController.redirectToTaxCreditsService" should {

    "redirect to tax-credits-service/renewals/service-router" in {

      val r = controller.redirectToTaxCreditsService()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe MOVED_PERMANENTLY
      redirectLocation(r) shouldBe Some("/tax-credits-service/renewals/service-router")
    }
  }

  "Calling PublicController.redirectToPersonalDetails" should {

    "redirect to /personal-details page" in {
      val r = controller.redirectToPersonalDetails()(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account/personal-details")
    }
  }

  "Calling PublicController.verifyEntryPoint" should {

    "redirect to /personal-account page with Verify auth provider" in {
      val request = FakeRequest("GET", "/personal-account/start-verify")
      val r = controller.verifyEntryPoint()(request)

      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account")
      session(r) shouldBe new Session(Map(SessionKeys.authProvider -> configDecorator.authProviderVerify))
    }
  }

  "Calling PublicController.governmentGatewayEntryPoint" should {

    "redirect to /personal-account page with GG auth provider" in {
      val request = FakeRequest("GET", "/personal-account/start-government-gateway")
      val r = controller.governmentGatewayEntryPoint()(request)

      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account")
      session(r) shouldBe new Session(Map(SessionKeys.authProvider -> configDecorator.authProviderGG))
    }
  }
}
