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
import controllers.auth.requests.UserRequest
import models.{CardHeading, CardType, HmrcCardModel}
import play.api.i18n.Messages
import viewmodels.TabEnum.{Activity, Task as TaskTab}
import viewmodels.{TabEnum, Task, TaskStatus}

import scala.concurrent.{ExecutionContext, Future}

class TabContentService @Inject() (
  tasksService: TasksService
)(implicit ec: ExecutionContext) {

  def getTaskAndTabCards(
    currentTab: TabEnum
  )(implicit request: UserRequest[?], messages: Messages): Future[TabContentCards] =
    tasksService.getListOfTasks.map { tasks =>
      val taskCards = toCards(tasks.filterNot(_.status == TaskStatus.Completed))
      val tabCards  = currentTab match {
        case TaskTab  => taskCards
        case Activity => toCards(tasks.filter(_.status == TaskStatus.Completed))
        case _        => Seq.empty
      }

      TabContentCards(
        taskCards = taskCards,
        tabCards = tabCards
      )
    }

  private def toCards(tasks: Seq[Task]): Seq[HmrcCardModel] =
    tasks.map { task =>
      HmrcCardModel(
        cardType = CardType.BasicCard,
        heading = CardHeading(
          text = task.title,
          url = Some(task.href),
          opensNewTab = false
        ),
        body = None,
        hint = None
      )
    }
}

final case class TabContentCards(
  taskCards: Seq[HmrcCardModel],
  tabCards: Seq[HmrcCardModel]
) {
  val taskCount: Int = taskCards.size
}
