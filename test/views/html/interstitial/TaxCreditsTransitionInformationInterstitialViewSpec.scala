/*
 * Copyright 2024 HM Revenue & Customs
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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import play.api.i18n.Messages
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class TaxCreditsTransitionInformationInterstitialViewSpec extends ViewSpec {

  lazy val taxCreditsTransitionInformationInterstitialView: TaxCreditsTransitionInformationInterstitialView =
    inject[TaxCreditsTransitionInformationInterstitialView]

  lazy implicit val configDecorator: ConfigDecorator            = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

  "Rendering TaxCreditsTransitionInformationInterstitialView.scala.html" must {

    "show the correct page title" in {
      val doc = asDocument(taxCreditsTransitionInformationInterstitialView().toString)
      doc.text() must include(Messages("tax_credits.transition.information.title"))
    }

    "display the transition end date information" in {
      val doc = asDocument(taxCreditsTransitionInformationInterstitialView().toString)
      doc.text() must include(Messages("tax_credits.transition.information.end_date"))
      doc.text() must include(Messages("tax_credits.transition.information.letter_info"))
      doc.text() must include(Messages("tax_credits.transition.information.end_date_notification"))
      doc.text() must include(Messages("tax_credits.transition.information.early_end_date_warning"))
    }

    "display the overpayment section" in {
      val doc = asDocument(taxCreditsTransitionInformationInterstitialView().toString)
      doc.text() must include(Messages("tax_credits.transition.information.overpayment.title"))
      doc.text() must include(Messages("tax_credits.transition.information.overpayment.details"))
      doc.text() must include(Messages("tax_credits.transition.information.dwp_contact_details_primary_information"))
      doc.text() must include(Messages("tax_credits.transition.information.dwp_contact_details_advisory_note"))
    }

    "display the support section" in {
      val doc = asDocument(taxCreditsTransitionInformationInterstitialView().toString)
      doc.text() must include(Messages("tax_credits.transition.information.support.title"))
      doc.text() must include(Messages("tax_credits.transition.information.support.details"))
      doc.text() must include(Messages("tax_credits.transition.information.support.universal_credit"))
      doc.text() must include(Messages("tax_credits.transition.information.support.pension_credit"))
      doc.text() must include(Messages("tax_credits.transition.information.support.contact"))
    }

    "include the correct links in the support section" in {
      val doc                 = asDocument(taxCreditsTransitionInformationInterstitialView().toString)
      val universalCreditLink = doc.select("a[href='https://www.gov.uk/universal-credit']")
      val pensionCreditLink   = doc.select("a[href='https://www.gov.uk/pension-credit']")

      universalCreditLink.text() mustBe Messages("tax_credits.transition.information.support.universal_credit")
      pensionCreditLink.text() mustBe Messages("tax_credits.transition.information.support.pension_credit")
    }
  }
}
