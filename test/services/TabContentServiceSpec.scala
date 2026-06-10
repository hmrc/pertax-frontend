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
import play.api.i18n.{Lang, Messages, MessagesImpl}
import testUtils.BaseSpec
import viewmodels.{PtapHomeContentSection, PtapHomeTab, Task, TaskStatus}

class TabContentServiceSpec extends BaseSpec {

  private val service                          = new TabContentService
  private implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

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

    "build only the Tasks card container for the task tab" in {
      val containers = service.getTabContent(PtapHomeTab.Task)

      containers.size mustBe 1
      containers.head.normalizedHeader.map(_.body) mustBe Some("Tasks")
      containers.head.cards mustBe PtapHomePlaceholderCardData.taskCards
    }

    "build only the Activities card container for the activity tab" in {
      val containers = service.getTabContent(PtapHomeTab.Activity)

      containers.size mustBe 1
      containers.head.normalizedHeader.map(_.body) mustBe Some("Activities")
      containers.head.cards mustBe PtapHomePlaceholderCardData.activityCards
    }

    "build no placeholder card containers for tabs owned by later stories" in {
      service.getTabContent(PtapHomeTab.Tax) mustBe empty
      service.getTabContent(PtapHomeTab.News) mustBe empty
      service.getTabContent(PtapHomeTab.Support) mustBe empty
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
        currentTab = PtapHomeTab.Activity,
        taskCount = 3
      )

      contentModel.containers.map(_.normalizedHeader.map(_.body)) mustBe Seq(Some("Activities"))
      contentModel.taskCount mustBe 3
    }

    "build a route-backed SecondaryNav model with a task count badge" in {
      val navModel = service.getSecondaryNavModel(PtapHomeTab.Activity, taskCount = 4)

      navModel.labelledBy mustBe Some("secondary-nav-label")
      navModel.items.map(_.href) mustBe PtapHomeTab.all.map(tab =>
        controllers.routes.HomeController.homePageTab(tab.key).url
      )
      navModel.items
        .find(_.href == controllers.routes.HomeController.homePageTab(PtapHomeTab.Activity.key).url)
        .map(_.current) mustBe Some(true)
      navModel.items
        .find(_.href == controllers.routes.HomeController.homePageTab(PtapHomeTab.Task.key).url)
        .flatMap(_.notificationCount) mustBe Some(4)
      navModel.items
        .filterNot(_.href == controllers.routes.HomeController.homePageTab(PtapHomeTab.Task.key).url)
        .flatMap(_.notificationCount) mustBe empty
    }

    "support empty card lists without losing the empty state" in {
      val container = service.getCardContainer(PtapHomeContentSection.Tasks, PtapHomePlaceholderCardData.emptyCards)

      container.cards mustBe empty
      container.emptyView.body must include("There are no tasks to show.")
    }
  }
}
