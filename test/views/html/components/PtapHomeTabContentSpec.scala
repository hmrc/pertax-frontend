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

import models.PtapHomePlaceholderCardData
import services.TabContentService
import views.html.ViewSpec
import viewmodels.PtapHomeContentSection

import scala.jdk.CollectionConverters.*

class PtapHomeTabContentSpec extends ViewSpec {

  lazy val ptapHomeTabContent: PtapHomeTabContent = inject[PtapHomeTabContent]

  private val tabContentService = new TabContentService

  "PtapHomeTabContent" must {

    "render a supplied CardContainerModel" in {
      val container = tabContentService.getCardContainer(
        section = PtapHomeContentSection.Tasks,
        cards = PtapHomePlaceholderCardData.taskCards
      )

      val document = asDocument(ptapHomeTabContent(Seq(container)).toString)

      document.select("#tasks-heading").text() mustBe "Tasks"
      PtapHomePlaceholderCardData.taskCards.foreach(card => document.text() must include(card.heading.text))
    }

    "render each CardContainerModel in a supplied list" in {
      val containers = Seq(
        tabContentService.getCardContainer(
          section = PtapHomeContentSection.Tasks,
          cards = PtapHomePlaceholderCardData.taskCards
        ),
        tabContentService.getCardContainer(
          section = PtapHomeContentSection.Activities,
          cards = PtapHomePlaceholderCardData.activityCards
        )
      )

      val document = asDocument(ptapHomeTabContent(containers).toString)

      document.select("h2").eachText().asScala.toSeq must contain allOf ("Tasks", "Activities")
      (PtapHomePlaceholderCardData.taskCards ++ PtapHomePlaceholderCardData.activityCards).foreach { card =>
        document.text() must include(card.heading.text)
        document.select(s"a[href='${card.heading.url.get}']").size() mustBe 1
      }
    }

    "render a safe empty state when the card list is empty" in {
      val container = tabContentService.getCardContainer(
        section = PtapHomeContentSection.Tasks,
        cards = PtapHomePlaceholderCardData.emptyCards
      )

      val document = asDocument(ptapHomeTabContent(Seq(container)).toString)

      document.text() must include("There are no tasks to show.")
      document.select(".hmrc-card").size() mustBe 0
      document.select("ul.hmrc-card__container").size() mustBe 0
    }
  }
}
