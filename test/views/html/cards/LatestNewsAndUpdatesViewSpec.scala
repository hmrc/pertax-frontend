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

import config.{ConfigDecorator, LocalTemplateRenderer}
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.renderer.TemplateRenderer
import views.html.ViewSpec
import views.html.cards.home.LatestNewsAndUpdatesView

class LatestNewsAndUpdatesViewSpec extends ViewSpec {
  val latestNewsAndUpdatesView: LatestNewsAndUpdatesView = app.injector.instanceOf[LatestNewsAndUpdatesView]

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  implicit val configDecorator: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  def hasLink(document: Document, content: String)(implicit messages: Messages): Assertion =
    document.getElementsMatchingText(content).hasAttr("href") mustBe true

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind(classOf[TemplateRenderer]).to(classOf[LocalTemplateRenderer])
      )
      .configure(
        Map(
          "feature.news.nicSection.short-description-en" -> "1.25 percentage points uplift in National Insurance contributions",
          "feature.news.nicSection.content-en" -> "<p class=\"govuk-body\">From 6 April 2022 to 5 April 2023 National Insurance contributions will increase by 1.25 percentage points. This will be spent on the NHS, health and social care in the UK.</p><p class=\"govuk-body\">The increase will apply to:</p><ul class=\"govuk-list govuk-list--bullet\"><li>Class 1 (paid by employees)</li><li>Class 4 (paid by self-employed)</li><li>secondary Class 1, 1A and 1B (paid by employers)</li></ul><p class=\"govuk-body\">The increase will not apply if you are over the State Pension age.</p><p class=\"govuk-body govuk-!-margin-bottom-3\"><a href=\"https://www.gov.uk/guidance/prepare-for-the-health-and-social-care-levy\" class=\"govuk-link\" target=\"_blank\" rel=\"noopener noreferrer\">Prepare for the Health and Social Care Levy (opens in new tab)</a></p>\"\n      short-description-cy: \"Cynnydd o 1.25 pwynt canrannol yng nghyfraniadau Yswiriant Gwladol\"\n      content-cy: \"<p class=\"govuk-body\">O 6 Ebrill 2022 i 5 Ebrill 2023, bydd cyfraniadau Yswiriant Gwladol yn cynyddu 1.25 pwynt canrannol. Caiff hyn ei wario ar y GIG ac ar iechyd a gofal cymdeithasol yn y DU.</p><p class=\"govuk-body\">Bydd y cynnydd yn berthnasol i’r canlynol:</p><ul class=\"govuk-list govuk-list--bullet\"><li>CYG Dosbarth 1 (a delir gan gyflogeion)</li><li>CYG Dosbarth 4 (a delir gan bobl hunangyflogedig)</li><li>CYG Dosbarth 1, 1A ac 1B eilaidd (a delir gan gyflogwyr)</li></ul><p class=\"govuk-body\">Ni fydd y cynnydd yn berthnasol os ydych dros oedran Pensiwn y Wladwriaeth.</p><p class=\"govuk-body govuk-!-margin-bottom-3\"><a href=\"https://www.gov.uk/guidance/prepare-for-the-health-and-social-care-levy.cy\" class=\"govuk-link\" target=\"_blank\" rel=\"noopener noreferrer\">Paratoi ar gyfer yr Ardoll Iechyd a Gofal Cymdeithasol (yn agor tab newydd)</a></p>",
          "feature.news.nicSection.start-date" -> "2022-04-01"
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

    "render the given url correctly" in {

      hasLink(
        doc,
        Messages("label.percentage_points_uplift_in_NIC")
      )

    }

    "render the given content correctly" in {

      doc.text() must include(Messages("label.percentage_points_uplift_in_NIC"))
    }
  }
}
