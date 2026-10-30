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

import controllers.auth.requests.UserRequest
import models.{CardType, TagColour}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import testUtils.{BaseSpec, UserRequestFixture}
import uk.gov.hmrc.domain.Generator
import viewmodels.TabEnum.{Activity, Task as TaskTab, Tax}
import viewmodels.{ActivityType, Task, TaskStatus}

import scala.concurrent.Future

class TabContentServiceSpec extends BaseSpec {

  val mockTasksService: TasksService = mock[TasksService]

  val tabContentService = new TabContentService(mockTasksService)

  private val generatedNino = new Generator().nextNino

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTasksService)
  }

  private implicit val userRequest: UserRequest[AnyContent] = UserRequestFixture.buildUserRequest(
    request = FakeRequest(),
    authNino = generatedNino
  )

  private implicit val messages: Messages = messagesApi.preferred(FakeRequest())

  "getTaskAndTabCards" must {
    "fetch tasks once and return task cards as the selected tab cards for the Tasks tab" in {
      val tasks = Seq(
        Task(
          "Tax task",
          TaskStatus.Incomplete,
          Some("/tax"),
          Some("You owe £100"),
          Some("Due 31 January 2027"),
          ActivityType.Task,
          Some(TagColour.Orange)
        ),
        Task(
          "Tax calculation",
          TaskStatus.Incomplete,
          Some("/tax-calculation"),
          Some("You paid too little tax"),
          Some("Updated 1 July 2026"),
          ActivityType.Event
        )
      )

      when(mockTasksService.getListOfTasks(any(), any()))
        .thenReturn(Future.successful(tasks))

      val result = tabContentService.getTaskAndTabCards(TaskTab).futureValue

      result.taskCards must have size 1
      result.tabCards  must have size 1
      result.taskCount mustBe 1
      result.taskCards.head.cardType mustBe CardType.BasicCard
      result.taskCards.head.heading.text mustBe "Tax task"
      result.taskCards.head.heading.url mustBe Some("/tax")
      result.taskCards.head.body.map(_.content.body) mustBe Some("You owe £100")
      result.taskCards.head.hint.map(_.content.body) mustBe Some("Due 31 January 2027")
      result.taskCards.head.hint.flatMap(_.tag_colour) mustBe Some(TagColour.Orange)
      result.tabCards.head.heading.text mustBe "Tax task"
      verify(mockTasksService).getListOfTasks(any(), any())
    }

    "return events as the selected tab cards for the Activity tab" in {
      val tasks = Seq(
        Task("Tax task", TaskStatus.Incomplete, "/tax", Some("You owe £100")),
        Task(
          "Tax calculation",
          TaskStatus.Incomplete,
          Some("/tax-calculation"),
          Some("You paid too little tax"),
          Some("Updated 1 July 2026"),
          ActivityType.Event,
          Some(TagColour.Green)
        )
      )

      when(mockTasksService.getListOfTasks(any(), any()))
        .thenReturn(Future.successful(tasks))

      val result = tabContentService.getTaskAndTabCards(Activity).futureValue

      result.taskCards must have size 1
      result.tabCards  must have size 1
      result.taskCount mustBe 1
      result.tabCards.head.cardType mustBe CardType.BasicCard
      result.tabCards.head.heading.text mustBe "Tax calculation"
      result.tabCards.head.heading.url mustBe Some("/tax-calculation")
      result.tabCards.head.body.map(_.content.body) mustBe Some("You paid too little tax")
      result.tabCards.head.hint.map(_.content.body) mustBe Some("Updated 1 July 2026")
      result.tabCards.head.hint.flatMap(_.tag_colour) mustBe Some(TagColour.Green)
    }

    "continue to return legacy completed tasks on the Activity tab" in {
      val tasks = Seq(
        Task("Legacy task", TaskStatus.Incomplete, "/task"),
        Task("Legacy activity", TaskStatus.Completed, "/activity")
      )

      when(mockTasksService.getListOfTasks(any(), any()))
        .thenReturn(Future.successful(tasks))

      val result = tabContentService.getTaskAndTabCards(Activity).futureValue

      result.taskCards.map(_.heading.text) mustBe Seq("Legacy task")
      result.tabCards.map(_.heading.text) mustBe Seq("Legacy activity")
    }

    "return no selected tab cards for tabs without card content" in {
      val tasks = Seq(
        Task("Tax task", TaskStatus.Incomplete, "/tax")
      )

      when(mockTasksService.getListOfTasks(any(), any()))
        .thenReturn(Future.successful(tasks))

      val result = tabContentService.getTaskAndTabCards(Tax).futureValue

      result.taskCards must have size 1
      result.tabCards mustBe empty
      result.taskCount mustBe 1
    }

    "return empty card lists when there are no tasks or activities" in {
      when(mockTasksService.getListOfTasks(any(), any()))
        .thenReturn(Future.successful(Seq.empty))

      val result = tabContentService.getTaskAndTabCards(TaskTab).futureValue
      result.taskCards mustBe empty
      result.tabCards mustBe empty
      result.taskCount mustBe 0
    }
  }
}
