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

package views.html.components

import play.twirl.api.Html
import org.jsoup.nodes.Document
import org.scalatest.matchers.must.Matchers
import views.html.ViewSpec
import models.{Body, CardHint, CardType, Heading, HmrcCardModel, Tag}

class HmrcCardSpec extends ViewSpec with Matchers {
  "HmrcCard component" must {
    "render correctly with BasicCard type HmrcCardModel." in {
      val model = HmrcCardModel(CardType.BasicCard, Heading(Html("Test Heading"), Some("/test"), false), None, None)

      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.select("div.hmrc-card").size mustBe 1
      doc.select("a[href=/test]").size mustBe 1
      doc.select("a[href]").text mustBe "Test Heading"
      doc.select("span.hmrc-card__chevron").size mustBe 1
    }
    "render correctly with BasicCardWithDueDate type HmrcCardModel." in {
      val model = HmrcCardModel(
        CardType.BasicCardWithDueDate,
        Heading(Html("Test Heading"), Some("/test"), false),
        None,
        Some(CardHint(None, Some(Tag(Html("Due 31 January 2025"), "govuk-tag govuk-tag--orange"))))
      )

      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.select("div.hmrc-card").size mustBe 1
      doc.select("a[href=/test]").size mustBe 1
      doc.select("a[href]").text mustBe "Test Heading"
      doc.select("span.hmrc-card__chevron").size mustBe 1
      doc.select("p.govuk-body").size mustBe 1
      doc.select("span.govuk-tag").size mustBe 1
      doc.select("span.govuk-tag").text mustBe "Due 31 January 2025"
    }
    "render correctly with SectionCard type HmrcCardModel." in {
      val model = HmrcCardModel(
        CardType.SectionCard,
        Heading(Html("Test Heading"), Some("/test"), false),
        Some(Body(Html("""We've received your Self Assessment tax return."""))),
        Some(CardHint(Some(Html("""Received 7 January 2024""")), None))
      )

      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.select("div.hmrc-card").size mustBe 1
      doc.select("a[href=/test]").size mustBe 1
      doc.select("a[href]").text mustBe "Test Heading"
      doc.select("span.hmrc-card__chevron").size mustBe 1
      doc.select("p.govuk-body").size mustBe 1
      doc.select("p.govuk-hint").size mustBe 1
      doc.select("p.govuk-body").text mustBe "We've received your Self Assessment tax return."
      doc.select("p.govuk-hint").text mustBe "Received 7 January 2024"
    }
    "render correctly with NoLinkCard type HmrcCardModel." in {
      val model = HmrcCardModel(
        CardType.NoLinkCard,
        Heading(Html("Test Heading"), None, false),
        Some(Body(Html("""Your tax information is available in your <a href="/test">Self Assessment</a>."""))),
        None
      )

      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.select("div.hmrc-card").size mustBe 1
      doc.select("h3.hmrc-card__heading").size mustBe 1
      doc.select("h3.hmrc-card__heading").text mustBe "Test Heading"
      doc.select("p.govuk-body").size mustBe 1
      doc.select("p.govuk-body").text mustBe "Your tax information is available in your Self Assessment."
    }
    "render correctly with NewTabCard type HmrcCardModel." in {
      val model = HmrcCardModel(CardType.NewTabCard, Heading(Html("Test Heading"), Some("/test"), true), None, None)

      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.select("div.hmrc-card").size mustBe 1
      doc.select("h3.hmrc-card__heading").size mustBe 1
      doc.select("h3.hmrc-card__heading").text mustBe "Test Heading (opens in new tab)"
      doc.select("a[href=/test]").size mustBe 1
      doc.select("span").size mustBe 2
      doc.select("span.hmrc-card__chevron").size mustBe 1
      doc.select("span.govuk-\\!-font-weight-regular").size mustBe 1
      doc.select("span.govuk-\\!-font-weight-regular").text mustBe "(opens in new tab)"
    }
    "render arbitrary html in BasicCardWithDueDate correctly, without characters escaping" in {
      val model = HmrcCardModel(
        CardType.BasicCardWithDueDate,
        Heading(Html("Test Heading"), Some("/test"), false),
        None,
        Some(CardHint(None, Some(Tag(Html("Due 31 January 2025"), "govuk-tag govuk-tag--orange"))))
      )

      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.html must contain noneOf ("&amp;", "&lt;", "&gt;", "&quot;", "&#x37;")
    }
    "render Arbitrary html in SectionCard correctly, without characters escaping" in {
      val model = HmrcCardModel(
        CardType.SectionCard,
        Heading(Html("Test Heading"), Some("/test"), false),
        Some(Body(Html("""We've received your Self Assessment tax return."""))),
        Some(CardHint(Some(Html("""Received 7 January 2024""")), None))
      )

      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.html must not contain ("&amp;", "&lt;", "&gt;", "&quot;", "&#x37;")

    }
    "render arbitrary html in NoLinkCard correctly, without characters escaping" in {
      val model = HmrcCardModel(
        CardType.NoLinkCard,
        Heading(Html("Test Heading"), None, false),
        Some(Body(Html("""Your tax information is available in your <a href="/test">Self Assessment</a>."""))),
        None
      )

      val doc = asDocument(views.html.components.HmrcCard(model).toString)
      doc.html must not contain ("&amp;", "&lt;", "&gt;", "&quot;", "&#x37;")

    }
  }

}
