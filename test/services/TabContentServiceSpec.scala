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

import models.PtapHomePlaceholderCardData
import testUtils.BaseSpec
import viewmodels.{PtapHomeContentSection, TabEnum, Task, TaskStatus}

class TabContentServiceSpec extends BaseSpec {

  private val service = new TabContentService

  "TabContentService" must {

    "build a CardContainerModel for Tasks content" in {
      val container = service.getCardContainer(
        section = PtapHomeContentSection.Tasks,
        cards = PtapHomePlaceholderCardData.taskCards
      )

      container.normalizedHeader.map(_.body) mustBe Some("Tasks")
      container.normalizedListAriaLabel mustBe Some("Tasks")
      container.normalizedHeaderId mustBe Some("tasks-heading")
      container.cards mustBe PtapHomePlaceholderCardData.taskCards
    }

    "build a CardContainerModel for Activities content" in {
      val container = service.getCardContainer(
        section = PtapHomeContentSection.Activities,
        cards = PtapHomePlaceholderCardData.activityCards
      )

      container.normalizedHeader.map(_.body) mustBe Some("Activities")
      container.normalizedListAriaLabel mustBe Some("Activities")
      container.normalizedHeaderId mustBe Some("activities-heading")
      container.cards mustBe PtapHomePlaceholderCardData.activityCards
    }

    "build only the Tasks card container for the Tasks content section" in {
      val containers = service.getTabContent(Some(PtapHomeContentSection.Tasks))

      containers.size mustBe 1
      containers.head.normalizedHeader.map(_.body) mustBe Some("Tasks")
      containers.head.cards mustBe PtapHomePlaceholderCardData.taskCards
    }

    "build only the Activities card container for the Activities content section" in {
      val containers = service.getTabContent(Some(PtapHomeContentSection.Activities))

      containers.size mustBe 1
      containers.head.normalizedHeader.map(_.body) mustBe Some("Activities")
      containers.head.cards mustBe PtapHomePlaceholderCardData.activityCards
    }

    "build no placeholder card containers when content section is None" in {
      service.getTabContent(None) mustBe empty
    }

    "map tab names to content sections" in {
      service.getContentSectionForTab(TabEnum.TASK.name) mustBe Some(PtapHomeContentSection.Tasks)
      service.getContentSectionForTab(TabEnum.ACTIVITY.name) mustBe Some(PtapHomeContentSection.Activities)
      service.getContentSectionForTab(TabEnum.TAX.name) mustBe None
      service.getContentSectionForTab(TabEnum.NEWS.name) mustBe None
      service.getContentSectionForTab(TabEnum.SUPPORT.name) mustBe None
      service.getContentSectionForTab("unknown") mustBe None
    }

    "calculate task count from the fetched task list" in {
      val tasks = Seq(
        Task("Task one", TaskStatus.Incomplete, "/task-one"),
        Task("Task two", TaskStatus.Incomplete, "/task-two")
      )

      service.getTaskCount(tasks) mustBe 2
    }

    "expose selected card containers and task count in a content model" in {
      val contentModel = service.getTabContentModel(
        tabName = TabEnum.ACTIVITY.name,
        taskCount = 3
      )

      contentModel.containers.map(_.normalizedHeader.map(_.body)) mustBe Seq(Some("Activities"))
      contentModel.taskCount mustBe 3
    }

    "support empty card lists without losing the empty state" in {
      val container = service.getCardContainer(PtapHomeContentSection.Tasks, PtapHomePlaceholderCardData.emptyCards)

      container.cards mustBe empty
      container.emptyView.body must include("There are no tasks to show.")
    }
  }
}
