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

package views.html.cards

import config.ConfigDecorator
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.i18n.Messages
import viewmodels.Message.text
import viewmodels.{Heading, TaxCalculationViewModel, TaxYears, UnderpaidUrl}
import views.html.ViewSpec
import views.html.cards.home.TaxCalculationView

import scala.collection.JavaConverters._

class TaxCalculationViewSpec extends ViewSpec {

  val taxCalculation = injected[TaxCalculationView]

  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]

  def hasLink(document: Document, content: String, href: String)(implicit messages: Messages): Assertion =
    document.getElementsMatchingText(content).hasAttr("href") mustBe true

  "TaxCalculation card" must {

    val previousTaxYear = 2017

    val doc =
      asDocument(
        taxCalculation(
          TaxCalculationViewModel(
            TaxYears(previousTaxYear, previousTaxYear + 1),
            Heading(
              text("label.you_do_not_owe_any_more_tax", previousTaxYear.toString, (previousTaxYear + 1).toString),
              UnderpaidUrl(previousTaxYear)
            ),
            List(text("label.you_have_no_payments_to_make_to_hmrc")),
            Nil
          )
        ).toString
      )

    "render the given heading correctly" in {

      doc.text() must include(
        Messages("label.you_do_not_owe_any_more_tax", previousTaxYear.toString, (previousTaxYear + 1).toString)
      )
    }

    "render the given url correctly" in {

      hasLink(
        doc,
        Messages("label.you_do_not_owe_any_more_tax", previousTaxYear.toString, (previousTaxYear + 1).toString),
        configDecorator.underpaidUrl(previousTaxYear)
      )
    }

    "render the given content correctly" in {

      doc.text() must include(Messages("label.you_have_no_payments_to_make_to_hmrc"))
    }
  }
}
