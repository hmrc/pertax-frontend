/*
 * Copyright 2022 HM Revenue & Customs
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
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.i18n.Messages
import play.api.test.FakeRequest
import util.DateTimeTools.current
import util.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class ViewItsaInterstitialHomeViewSpec extends ViewSpec {

  lazy val viewItsaInterstitialHomeView = injected[ViewItsaInterstitialHomeView]

  lazy implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val userRequest = buildUserRequest(request = FakeRequest())
  def hasLink(document: Document, content: String, href: String)(implicit messages: Messages): Assertion =
    document.getElementsMatchingText(content).hasAttr("href") mustBe true
  val currentTaxYear = current.currentYear.toString
  val currentTaxYearMinusOne = current.previous.currentYear.toString
  val currentTaxYearMinusTwo = current.previous.previous.currentYear.toString

  "Rendering ViewItsaInterstitialHomeView.scala.html" must {

    "show content for Itsa" in {

      val doc =
        asDocument(
          viewItsaInterstitialHomeView(
            s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home",
            currentTaxYear,
            currentTaxYearMinusOne,
            currentTaxYearMinusTwo,
            true,
            false,
            false
          ).toString
        )

      doc.text() must include(Messages("label.your_self_assessment"))
      doc.text() must include(Messages("label.current_tax_year_range", currentTaxYearMinusOne, currentTaxYear))
// TODO
//      hasLink(
//        doc,
//        Messages("label.making_tax_digital"),
//        ""
//      )
    }

    "show content for SA" in {

      val doc =
        asDocument(
          viewItsaInterstitialHomeView(
            s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home",
            currentTaxYear,
            currentTaxYearMinusOne,
            currentTaxYearMinusTwo,
            false,
            true,
            false
          ).toString
        )

      doc.text() must include(Messages("label.your_self_assessment"))
      doc.text() must include(Messages("label.previous_tax_year_range", currentTaxYearMinusTwo, currentTaxYearMinusOne))

      hasLink(
        doc,
        Messages("label.view_and_manage_your_earlier_self_assessment_years"),
        "/personal-account/self-assessment-summary"
      )
    }

    "show content for Seiss" in {

      val doc =
        asDocument(
          viewItsaInterstitialHomeView(
            s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home",
            currentTaxYear,
            currentTaxYearMinusOne,
            currentTaxYearMinusTwo,
            false,
            false,
            true
          ).toString
        )

      doc.text() must include(Messages("label.your_self_assessment"))
      doc.text() must include(Messages("title.seiss"))

      hasLink(
        doc,
        Messages("body.seiss"),
        s"${configDecorator.seissClaimsUrl}"
      )
    }

    "show content for Itsa , SA , Seiss when all the conditions are true" in {

      val doc =
        asDocument(
          viewItsaInterstitialHomeView(
            s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home",
            currentTaxYear,
            currentTaxYearMinusOne,
            currentTaxYearMinusTwo,
            true,
            true,
            true
          ).toString
        )

      doc.text() must include(Messages("label.your_self_assessment"))
      doc.text() must include(Messages("title.seiss"))
      doc.text() must include(Messages("label.current_tax_year_range", currentTaxYearMinusOne, currentTaxYear))
      doc.text() must include(Messages("label.previous_tax_year_range", currentTaxYearMinusTwo, currentTaxYearMinusOne))

      hasLink(
        doc,
        Messages("label.view_and_manage_your_earlier_self_assessment_years"),
        "/personal-account/self-assessment-summary"
      )
      hasLink(
        doc,
        Messages("body.seiss"),
        s"${configDecorator.seissClaimsUrl}"
      )
    }
  }
}
