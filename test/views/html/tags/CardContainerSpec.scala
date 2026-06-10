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

package views.html.tags

import play.twirl.api.Html
import views.html.ViewSpec
import viewmodels.CardContainerModel
import models.{CardHeading, CardType, HmrcCardModel}

class CardContainerSpec extends ViewSpec {

  lazy val cardContainer = inject[views.html.tags.CardContainer]

  private val cardOne: HmrcCardModel =
    HmrcCardModel(CardType.BasicCard, CardHeading("Card 1", Some("#card-one"), false), None, None)

  private val cardTwo: HmrcCardModel =
    HmrcCardModel(CardType.BasicCard, CardHeading("Card 2", Some("#card-two"), false), None, None)

  "CardContainer" must {

    "render emptyView when cards is empty" in {
      val model = CardContainerModel(
        emptyView = Html("<p>No cards</p>"),
        header = Some("Test Header"),
        cards = Seq.empty
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("h2.govuk-heading-m").text() mustBe "Test Header"
      doc.text() must include("No cards")
      doc.select("ul").size() mustBe 0
      doc.select(".hmrc-card").size() mustBe 0
    }

    "render header when provided" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("Test Header"),
        cards = Seq(cardOne)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("h2.govuk-heading-m").text() mustBe "Test Header"
      doc.select("h2.govuk-heading-m").attr("id") mustBe "card-container-header"
    }

    "render single card without ul wrapper" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        cards = Seq(cardOne)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("ul").size() mustBe 0
      doc.select(".hmrc-card").size() mustBe 1
      doc.select(".hmrc-card a").text() mustBe "Card 1"
    }

    "render multiple cards in ul with li wrappers" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        cards = Seq(cardOne, cardTwo),
        header = Some("Test Header"),
        headerId = Some("test-container")
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("ul.hmrc-card__container").size() mustBe 1
      doc.select("ul.hmrc-card__container > li.hmrc-card__list-item").size() mustBe 2
      doc.select("ul.hmrc-card__container > li.hmrc-card__list-item > div.hmrc-card").size() mustBe 2
    }

    "render a native list so screen readers can announce the item count" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        cards = Seq(cardOne, cardTwo),
        header = Some("Test Header")
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("ul.hmrc-card__container").hasAttr("role") mustBe false
      doc.select("ul.hmrc-card__container").first().children().size() mustBe 2
      doc.select("ul.hmrc-card__container > li").size() mustBe 2
    }

    "use aria-label from listAriaLabel when provided (precedence over header)" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("Test Header"),
        listAriaLabel = Some("Custom label"),
        cards = Seq(cardOne, cardTwo)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("ul").attr("aria-label") mustBe "Custom label"
      doc.select("ul").hasAttr("aria-labelledby") mustBe false
    }

    "use aria-label without rendering a heading when listAriaLabel is provided without header" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        listAriaLabel = Some("Custom label"),
        cards = Seq(cardOne, cardTwo)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("h2.govuk-heading-m").size() mustBe 0
      doc.select("ul").attr("aria-label") mustBe "Custom label"
      doc.select("ul").hasAttr("aria-labelledby") mustBe false
    }

    "use aria-labelledby from header when listAriaLabel not provided" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("Test Header"),
        cards = Seq(cardOne, cardTwo)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("ul").attr("aria-labelledby") mustBe "card-container-header"
      doc.select("ul").hasAttr("aria-label") mustBe false
    }

    "use custom headerId for both heading id and aria-labelledby" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("Test Header"),
        headerId = Some("custom-container-id"),
        cards = Seq(cardOne, cardTwo)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("h2").attr("id") mustBe "custom-container-id"
      doc.select("ul").attr("aria-labelledby") mustBe "custom-container-id"
    }

    "ignore empty listAriaLabel and use aria-labelledby from header" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("Test Header"),
        listAriaLabel = Some("   "), // Whitespace only
        cards = Seq(cardOne, cardTwo)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("ul").attr("aria-labelledby") mustBe "card-container-header"
      doc.select("ul").hasAttr("aria-label") mustBe false
    }

    "ignore empty headerId and use default" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("Test Header"),
        headerId = Some("   "), // Whitespace only
        cards = Seq(cardOne, cardTwo)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("h2").attr("id") mustBe "card-container-header"
      doc.select("ul").attr("aria-labelledby") mustBe "card-container-header"
    }

    "ignore empty header and not render heading" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("   "), // Whitespace only
        cards = Seq(cardOne)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("h2").size() mustBe 0
    }

    "render configured heading level" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("Test Header"),
        headingLevel = " H3 ",
        cards = Seq(cardOne)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("h3.govuk-heading-m").text() mustBe "Test Header"
    }

    "render an HTML header when provided" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some(Html("""<span><span class="govuk-visually-hidden">Section: </span>HTML Header</span>""")),
        cards = Seq(cardOne)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("h2 .govuk-visually-hidden").text() mustBe "Section:"
      doc.select("h2").text() mustBe "Section: HTML Header"
    }

    "escape a string header" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("<script>alert('xss')</script>"),
        cards = Seq(cardOne)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("script").size() mustBe 0
      doc.select("h2").text() mustBe "<script>alert('xss')</script>"
    }

    "render cards with focusable controls" in {
      val model = CardContainerModel(
        emptyView = Html(""),
        header = Some("Test Header"),
        cards = Seq(cardOne, cardTwo)
      )
      val doc   = asDocument(cardContainer(model).toString)
      doc.select("ul.hmrc-card__container > li.hmrc-card__list-item > div.hmrc-card a[href]").size() mustBe 2
    }

    "throw error when a card has no focusable control" in {
      an[Exception] must be thrownBy
        HmrcCardModel(CardType.BasicCard, CardHeading("Card 1", None, false), None, None)
    }

    "throw error for invalid heading level" in {
      an[IllegalArgumentException] must be thrownBy
        CardContainerModel(
          emptyView = Html(""),
          headingLevel = "h7",
          cards = Seq(cardOne)
        )
    }

    "throw error when cards >= 2 and no accessible name" in {
      an[IllegalArgumentException] must be thrownBy
        CardContainerModel(
          emptyView = Html(""),
          cards = Seq(cardOne, cardTwo)
        )
    }
  }
}
