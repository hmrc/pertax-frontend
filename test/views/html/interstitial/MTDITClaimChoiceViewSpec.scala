/*
 * Copyright 2026 HM Revenue & Customs
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

package views.html.interstitial

import controllers.auth.requests.UserRequest
import models.{ClaimMtdFromPtaChoiceFormProvider, ClaimMtdFromPtaChoiceModel}
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec
import models.ClaimMtdFromPtaChoiceFormProvider
import play.api.data.Form
import play.api.mvc.AnyContentAsEmpty

class MTDITClaimChoiceViewSpec extends ViewSpec {

  lazy val view: MTDITClaimChoiceView             = inject[MTDITClaimChoiceView]
  lazy val form: Form[ClaimMtdFromPtaChoiceModel] = ClaimMtdFromPtaChoiceFormProvider.form

  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  "Rendering MTDITClaimChoiceView.scala.html" must {

    "display the expected page header" in {
      val document = asDocument(view(controllers.routes.ClaimMtdFromPtaController.submit, form).toString)
      document.body().toString must include("Making Tax Digital for Income Tax")
    }

    "display the expected paragraph content with link" in {
      val document = asDocument(view(controllers.routes.ClaimMtdFromPtaController.submit, form).toString)

      document.body().toString must include("You are signed up for")
      document.body().toString must include("Making Tax Digital for Income Tax")
      document.body().toString must include(
        "You need to add the service to your personal tax account before you can access it."
      )

      val linkElement = document.select("a.govuk-link.govuk-link--no-visited-state")
      linkElement.text() mustBe "Making Tax Digital for Income Tax"
      linkElement.attr("href") mustBe
        "https://www.gov.uk/government/publications/extension-of-making-tax-digital-for-income-tax-self-assessment-to-sole-traders-and-landlords/making-tax-digital-for-income-tax-self-assessment-for-sole-traders-and-landlords"
      linkElement.attr("target") mustBe "_blank"
      linkElement.attr("rel") must include("noreferrer")
      linkElement.attr("rel") must include("noopener")
    }

    "display the expected question" in {
      val document = asDocument(view(controllers.routes.ClaimMtdFromPtaController.submit, form).toString)
      document.body().toString must include(
        "Do you want to add Making Tax Digital for Income Tax to your personal tax account now?"
      )
    }

    "display yes and no radio options" in {
      val document = asDocument(view(controllers.routes.ClaimMtdFromPtaController.submit, form).toString)

      val yesRadio = document.select("#mtd-choice-yes")
      yesRadio.size() mustBe 1
      yesRadio.attr("value") mustBe "true"

      val noRadio = document.select("#mtd-choice-no")
      noRadio.size() mustBe 1
      noRadio.attr("value") mustBe "false"
    }

    "display continue button" in {
      val document = asDocument(view(controllers.routes.ClaimMtdFromPtaController.submit, form).toString)
      document.select("button.govuk-button").text() mustBe "Continue"
    }

    "render a form posting to the expected action" in {
      val postAction = controllers.routes.ClaimMtdFromPtaController.submit
      val document   = asDocument(view(postAction, form).toString)

      val formHtml = document.select("form")
      formHtml.size() mustBe 1
      formHtml.attr("action") mustBe postAction.url
      formHtml.attr("method") mustBe "POST"
    }
  }
}
