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
import models.NonFilerSelfAssessmentUser
import org.jsoup.nodes.Document
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class SelfAssessmentRegistrationPageViewSpec extends ViewSpec {

  lazy val selfAssessmentRegistrationPageView: SelfAssessmentRegistrationPageView =
    inject[SelfAssessmentRegistrationPageView]

  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = FakeRequest())

  private val checkTaxReturnUrl = "https://www.gov.uk/check-if-you-need-tax-return"
  private val registerForSaUrl  = "https://www.gov.uk/register-for-self-assessment"
  private val saGuidanceUrl     = "https://www.gov.uk/browse/tax/self-assessment"

  "Rendering SelfAssessmentRegistrationPageView.scala.html" must {

    "show english content" when {
      lazy val doc: Document = asDocument(selfAssessmentRegistrationPageView().toString)

      "rendering the page title" in {
        doc.title() must include("Your Self Assessment")
      }

      "rendering the heading" in {
        doc.select("h1").text() mustBe "Self Assessment tax returns"
      }

      "rendering the warning text" in {
        doc.select(".govuk-warning-text__text").text() must include(
          "You are not currently registered for Self Assessment."
        )
      }

      "rendering the body paragraphs" in {
        assertContainsText(doc, "Self Assessment is a system HMRC uses to collect Income Tax.")
        assertContainsText(
          doc,
          "Tax is usually deducted automatically from wages and pensions. People and businesses with other income must report it in a Self Assessment tax return."
        )
        doc.text() must include(
          "You can Check if you need to send a tax return if you’re not sure, or register for Self Assessment."
        )
      }

      "rendering the links" in {
        assertContainsLink(doc, "Check if you need to send a tax return", checkTaxReturnUrl)
        assertContainsLink(doc, "register for Self Assessment.", registerForSaUrl)
        assertContainsLink(doc, "Self Assessment guidance", saGuidanceUrl)
      }

      "rendering the more information section" in {
        doc.select("h2").text() must include("More Information")
        doc.select("ul.govuk-list--bullet li a").attr("href") mustBe saGuidanceUrl
      }
    }

    "show welsh content" when {
      lazy val doc: Document =
        asDocument(selfAssessmentRegistrationPageView()(userRequest, welshMessages).toString)

      "rendering the page title" in {
        doc.title() must include("Eich Hunanasesiad")
      }

      "rendering the heading" in {
        doc.select("h1").text() mustBe "Ffurflenni Treth Hunanasesiad"
      }

      "rendering the warning text" in {
        doc.select(".govuk-warning-text__text").text() must include(
          "Nid ydych wedi cofrestru ar gyfer Hunanasesiad ar hyn o bryd."
        )
      }

      "rendering the body paragraphs" in {
        assertContainsText(doc, "System y mae CThEF yn ei defnyddio i gasglu Treth Incwm yw Hunanasesiad.")
        assertContainsText(
          doc,
          "Fel arfer, didynnir treth yn awtomatig oddi wrth gyflogau a phensiynau. Os oes gan bobl a busnesau incwm arall, mae’n rhaid iddynt roi gwybod amdano mewn Ffurflen Dreth Hunanasesiad."
        )
      }

      "rendering the links" in {
        assertContainsLink(doc, "a oes angen i chi anfon Ffurflen Dreth", checkTaxReturnUrl)
        assertContainsLink(doc, "gofrestru ar gyfer Hunanasesiad.", registerForSaUrl)
        assertContainsLink(doc, "Arweiniad Hunanasesiad", saGuidanceUrl)
      }

      "rendering the more information section" in {
        doc.select("h2").text() must include("Rhagor o wybodaeth")
      }
    }
  }
}
