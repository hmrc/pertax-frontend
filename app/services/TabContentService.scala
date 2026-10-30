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
import models.{CardBody, CardHeading, CardHint, CardType, HmrcCardModel}
import play.api.i18n.Messages
import play.twirl.api.Html
import viewmodels.TabEnum.{Activity, Task as TaskTab}
import viewmodels.{ActivityType, TabEnum, Task, TaskStatus}

import scala.concurrent.{ExecutionContext, Future}

class TabContentService @Inject() (
  tasksService: TasksService
)(implicit ec: ExecutionContext) {

  def getTaskAndTabCards(
    currentTab: TabEnum
  )(implicit request: UserRequest[?], messages: Messages): Future[TabContentCards] =
    tasksService.getListOfTasks.map { tasks =>
      val (activityTasks, incompleteTasks) = tasks.partition(isActivity)
      val taskCards                        = toCards(incompleteTasks)
      val tabCards                         = currentTab match {
        case TaskTab  => taskCards
        case Activity => toCards(activityTasks)
        case _        => Seq.empty
      }

      TabContentCards(
        taskCards = taskCards,
        tabCards = tabCards
      )
    }

  private def isActivity(task: Task): Boolean =
    task.activityType == ActivityType.Event || task.status == TaskStatus.Completed

  private def toCards(tasks: Seq[Task]): Seq[HmrcCardModel] =
    tasks.map { task =>
      HmrcCardModel(
        cardType = CardType.BasicCard,
        heading = CardHeading(
          text = task.title,
          url = task.href,
          opensNewTab = false
        ),
        body = task.hintText.map(s => CardBody(Html(s))),
        hint = task.dateText.map(s => CardHint(Html(s), task.dateTag))
      )
    }
}

final case class TabContentCards(
  taskCards: Seq[HmrcCardModel],
  tabCards: Seq[HmrcCardModel]
) {
  val taskCount: Int = taskCards.size
}
