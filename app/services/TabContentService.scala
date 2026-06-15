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

package services

import com.google.inject.Inject
import models.*
import play.twirl.api.Html
import viewmodels.{CardContainerModel, PtapHomeContentSection, PtapHomeTabContentModel, TabEnum, Task}

class TabContentService @Inject() () {

  def getTaskCards: Seq[HmrcCardModel] =
    PtapHomePlaceholderCardData.taskCards

  def getActivityCards: Seq[HmrcCardModel] =
    PtapHomePlaceholderCardData.activityCards

  def getTaskCount(tasks: Seq[Task]): Int =
    tasks.size

  def getCardContainer(section: PtapHomeContentSection, cards: Seq[HmrcCardModel]): CardContainerModel =
    CardContainerModel(
      emptyView = Html(s"""<p class="govuk-body">${section.emptyMessage}</p>"""),
      header = Some(section.containerHeading),
      cards = cards,
      headingLevel = "h2",
      listAriaLabel = Some(section.listAriaLabel),
      headerId = Some(section.headerId)
    )

  /** Maps a tab name (from TabEnum) to its corresponding content section. Only TASK and ACTIVITY tabs have PTAD-142
    * content. Other tabs return None (no placeholder content).
    */
  def getContentSectionForTab(tabName: String): Option[PtapHomeContentSection] =
    tabName match {
      case TabEnum.TASK.name     => Some(PtapHomeContentSection.Tasks)
      case TabEnum.ACTIVITY.name => Some(PtapHomeContentSection.Activities)
      case _                     => None
    }

  def getTabContent(
    contentSection: Option[PtapHomeContentSection]
  ): Seq[CardContainerModel] =
    contentSection.map { section =>
      getCardContainer(
        section = section,
        cards = section match {
          case PtapHomeContentSection.Tasks      => getTaskCards
          case PtapHomeContentSection.Activities => getActivityCards
        }
      )
    }.toSeq

  def getTabContentModel(
    tabName: String,
    taskCount: Int
  ): PtapHomeTabContentModel =
    PtapHomeTabContentModel(
      containers = getTabContent(getContentSectionForTab(tabName)),
      taskCount = taskCount
    )
}
