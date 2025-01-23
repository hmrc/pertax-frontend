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

class TaxCreditsEndedInformationInterstitialViewSpec extends ViewSpec {

  lazy val taxCreditsEndedInformationInterstitialView: TaxCreditsEndedInformationInterstitialView =
    inject[TaxCreditsEndedInformationInterstitialView]

  lazy implicit val configDecorator: ConfigDecorator            = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

  "Rendering TaxCreditsEndedInformationInterstitialView.scala.html" must {

    "show the correct page title" in {
      val doc = asDocument(taxCreditsEndedInformationInterstitialView().toString)
      doc.text() must include(Messages("tax_credits.ended.information.title"))
    }

    "display the paragraphs" in {
      val doc = asDocument(taxCreditsEndedInformationInterstitialView().toString)
      doc.text() must include(Messages("tax_credits.ended.information.p1"))
      doc.text() must include(Messages("tax_credits.ended.information.p2"))
      doc.text() must include(Messages("tax_credits.ended.information.p3"))
    }

    "display the h2" in {
      val doc = asDocument(taxCreditsEndedInformationInterstitialView().toString)
      doc.text() must include(Messages("tax_credits.ended.information.h2"))
    }


    "include the correct links" in {
      val doc                 = asDocument(taxCreditsEndedInformationInterstitialView().toString)
      val universalCreditLink = doc.select("a[href='https://www.gov.uk/universal-credit']")
      val pensionCreditLink   = doc.select("a[href='https://www.gov.uk/pension-credit']")

      universalCreditLink.text() mustBe Messages("tax_credits.ended.information.how.apply.li1")
      pensionCreditLink.text() mustBe Messages("tax_credits.ended.information.how.apply.li2")
    }
  }
}
