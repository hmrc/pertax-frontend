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
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.guice.GuiceApplicationBuilder
import views.html.ViewSpec
import views.html.cards.home.LatestNewsAndUpdatesView

class LatestNewsAndUpdatesViewSpec extends ViewSpec {
  val latestNewsAndUpdatesView: LatestNewsAndUpdatesView = app.injector.instanceOf[LatestNewsAndUpdatesView]

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  implicit val configDecorator: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  override protected def localGuiceApplicationBuilder(extraConfigValues: Map[String, Any]): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(
        Map(
          "feature.news.items.0.name"                 -> "nicSection",
          "feature.news.items.0.short-description-en" -> "1.25 percentage points uplift in National Insurance contributions",
          "feature.news.items.0.content-en"           -> "<p class=\"govuk-body\">From 6 April 2022 to 5 April 2023 National Insurance contributions will increase by 1.25 percentage points. This will be spent on the NHS, health and social care in the UK.</p><p class=\"govuk-body\">The increase will apply to:</p><ul class=\"govuk-list govuk-list--bullet\"><li>Class 1 (paid by employees)</li><li>Class 4 (paid by self-employed)</li><li>secondary Class 1, 1A and 1B (paid by employers)</li></ul><p class=\"govuk-body\">The increase will not apply if you are over the State Pension age.</p><p class=\"govuk-body govuk-!-margin-bottom-3\"><a href=\"https://www.gov.uk/guidance/prepare-for-the-health-and-social-care-levy\" class=\"govuk-link\" target=\"_blank\" rel=\"noopener noreferrer\">Prepare for the Health and Social Care Levy (opens in new tab)</a></p>",
          "feature.news.items.0.start-date"           -> "2022-04-01"
        )
      )

  "LatestNewsAndUpdates card" must {

    val doc =
      asDocument(
        latestNewsAndUpdatesView().toString
      )

    "render the given heading correctly" in {

      doc.text() must include(
        Messages("label.latest_news_and_updates")
      )
    }

    "render the given url correctly" in
      hasLink(
        doc,
        Messages("label.percentage_points_uplift_in_NIC")
      )

    "render the given content correctly" in {

      doc.text() must include(Messages("label.percentage_points_uplift_in_NIC"))
    }

    "render the correct HTML" in {
      val expectedView =
        """
          |<div class="card" id="news-card">
          |  <div class="card-body card-body-news">
          |    <h2 class="govuk-heading-s card-heading card-heading-news">
          |      Latest news and updates
          |    </h2>
          |    <p class="govuk-body"><a class="ga-track-anchor-click govuk-link govuk-link--no-visited-state" href="/personal-account/news/nicSection" data-ga-event-category="link - click" data-ga-event-action="Income" data-ga-event-label="1.25 percentage points uplift in National Insurance contributions">1.25 percentage points uplift in National Insurance contributions</a></p>
          |  </div>
          |</div>
          |""".stripMargin.replaceAll("\\s", "")

      val originalView = latestNewsAndUpdatesView().toString.trim.replaceAll("\\s", "")

      originalView mustBe expectedView
    }
  }
}
