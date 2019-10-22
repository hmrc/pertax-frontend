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

import config.ConfigDecorator
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.test.Helpers._
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.renderer.TemplateRenderer
import util.BaseSpec
import util.Fixtures._

class PublicControllerSpec extends BaseSpec with MockitoSugar {

  val mockTemplateRenderer = mock[TemplateRenderer]

  def controller = new PublicController(injected[MessagesApi])(
    mockLocalPartialRetriever,
    injected[ConfigDecorator],
    mockTemplateRenderer
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

}
