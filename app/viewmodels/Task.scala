/*
 * Copyright 2025 HM Revenue & Customs
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

package viewmodels

final case class Task(
  title: String,
  status: TaskStatus,
  href: Option[String],
  hintText: Option[String] = None,
  dateText: Option[String] = None,
  activityType: ActivityType = ActivityType.Task
)

object Task {
  def apply(title: String, status: TaskStatus, href: String): Task =
    Task(title, status, Some(href))

  def apply(title: String, status: TaskStatus, href: String, hintText: Option[String]): Task =
    Task(title, status, Some(href), hintText)
}

enum ActivityType {
  case Task
  case Event
}

enum TaskStatus(
  val classes: String = ""
) {

  case Incomplete extends TaskStatus(classes = "govuk-tag--red")
  case Completed extends TaskStatus(classes = "govuk-tag--blue")

}
