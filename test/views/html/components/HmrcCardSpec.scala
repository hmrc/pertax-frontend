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
import models.{HmrcCardModel,CardType,Heading,Body,CardHint,Tag}

class HmrcCardSpec extends ViewSpec with Matchers {
  "HmrcCard component" must {

    "render correctly with BasicCard CardType value" in {
      val model = HmrcCardModel(CardType.BasicCard,Heading("Test Heading",Some("/test")),None,None)
      
      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.select("div.hmrc-card").size mustBe 1
      doc.select("a[href=/test]").size mustBe 1
      doc.select("a[href]").text mustBe "Test Heading"
      doc.select("span.hmrc-card__chevron").size mustBe 1
    }
    "render correctly with BasicCardWithDueDate CardType value" in {
      val model = HmrcCardModel(
        CardType.BasicCardWithDueDate,
        Heading("Test Heading",Some("/test"))
        ,None,Some(CardHint(None,Some(Tag("Due 31 January 2025","govuk-tag govuk-tag--orange"))))
      )
      
      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.select("div.hmrc-card").size mustBe 1
      doc.select("a[href=/test]").size mustBe 1
      doc.select("a[href]").text mustBe "Test Heading"
      doc.select("span.hmrc-card__chevron").size mustBe 1
      doc.select("p.govuk-body").size mustBe 1
      doc.select("strong.govuk-tag").size mustBe 1
      doc.select("strong.govuk-tag").text mustBe "Due 31 January 2025"
    }
    "render correctly with SectionCard CardType value" in {
      val model = HmrcCardModel(
        CardType.SectionCard,
        Heading("Test Heading",Some("/test"))
        ,Some(Body(Html(
          """
          <p class="govuk-body">We've received your Self Assessment tax return.</p>
          <p class="govuk-hint">Received 7 January 2024</p>
          """
      ))),None)
      
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
    "render correctly with NoLinkCard CardType value" in {
      val model = HmrcCardModel(
        CardType.NoLinkCard,
        Heading("Test Heading",None),
        Some(Body(Html(
          """
          <p class="govuk-body">
            Your tax information is available in your <a href="/test">Self Assessment</a>.
          </p>
          """
      ))),None)
      
      val doc = asDocument(views.html.components.HmrcCard(model).toString)

      doc.select("div.hmrc-card").size mustBe 1
      doc.select("h3.hmrc-card__heading").size mustBe 1
      doc.select("h3.hmrc-card__heading").text mustBe "Test Heading"
      doc.select("p.govuk-body").size mustBe 1
      doc.select("p.govuk-body").text mustBe "Your tax information is available in your Self Assessment."
    }
  }
}