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

package views.html.cards

import config.ConfigDecorator
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.i18n.Messages
import views.html.ViewSpec
import views.html.cards.home.LatestNewsAndUpdatesView

class LatestNewsAndUpdatesViewSpec extends ViewSpec {

  val latestNewsAndUpdatesView = injected[LatestNewsAndUpdatesView]
  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]

  def hasLink(document: Document, content: String, href: String)(implicit messages: Messages): Assertion =
    document.getElementsMatchingText(content).hasAttr("href") mustBe true

  "TaxCalculation card" must {

    val doc =
      asDocument(
        latestNewsAndUpdatesView().toString
      )

    "render the given heading correctly" in {

      doc.text() must include(
        Messages("label.latest_news_and_updates")
      )
    }

    "render the given url correctly" in {

      hasLink(doc, Messages("label.stop_using_Verify"), s"configDecorator.pertaxFrontendHomeUrl/personal-account/news")

    }

    "render the given content correctly" in {

      doc.text() must include(Messages("label.stop_using_Verify"))
    }
  }
}
