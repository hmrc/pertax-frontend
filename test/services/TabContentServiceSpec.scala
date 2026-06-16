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
import models.CardType
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import testUtils.{BaseSpec, UserRequestFixture}
import uk.gov.hmrc.domain.Generator
import viewmodels.{Task, TaskStatus}

import scala.concurrent.Future

class TabContentServiceSpec extends BaseSpec {

  val mockTasksService: TasksService = mock[TasksService]

  val tabContentService = new TabContentService(mockTasksService)

  private val generatedNino = new Generator().nextNino

  private implicit val userRequest: UserRequest[AnyContent] = UserRequestFixture.buildUserRequest(
    request = FakeRequest(),
    authNino = generatedNino
  )

  private implicit val messages: Messages = messagesApi.preferred(FakeRequest())

  "getTaskCount" must {
    "return the number of tasks" in {
      val tasks = Seq(
        Task("Task 1", TaskStatus.Incomplete, "/task1"),
        Task("Task 2", TaskStatus.Completed, "/task2")
      )

      when(mockTasksService.getListOfTasks(any(), any()))
        .thenReturn(Future.successful(tasks))

      val result = tabContentService.getTaskCount.futureValue
      result mustBe 2
    }

    "return 0 when there are no tasks" in {
      when(mockTasksService.getListOfTasks(any(), any()))
        .thenReturn(Future.successful(Seq.empty))

      val result = tabContentService.getTaskCount.futureValue
      result mustBe 0
    }
  }

  "getTaskCards" must {
    "convert tasks to HmrcCardModels" in {
      val tasks = Seq(
        Task("Tax task", TaskStatus.Incomplete, "/tax", Some("You owe £100")),
        Task("Refund task", TaskStatus.Completed, "/refund", None)
      )

      when(mockTasksService.getListOfTasks(any(), any()))
        .thenReturn(Future.successful(tasks))

      val result = tabContentService.getTaskCards.futureValue

      result must have size 2
      result.head.cardType mustBe CardType.BasicCard
      result.head.heading.text mustBe "Tax task"
      result.head.heading.url mustBe Some("/tax")
    }

    "return empty sequence when there are no tasks" in {
      when(mockTasksService.getListOfTasks(any(), any()))
        .thenReturn(Future.successful(Seq.empty))

      val result = tabContentService.getTaskCards.futureValue
      result mustBe empty
    }
  }

  "getActivitiesCards" must {
    "return an empty sequence until activity data is wired" in {
      val result = tabContentService.getActivitiesCards.futureValue
      result mustBe empty
    }
  }
}
