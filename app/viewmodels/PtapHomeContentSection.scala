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

package viewmodels

enum PtapHomeContentSection(
  val containerHeading: String,
  val emptyMessage: String,
  val headerId: String,
  val listAriaLabel: String
):
  case Tasks
      extends PtapHomeContentSection(
        containerHeading = "Tasks",
        emptyMessage = "There are no tasks to show.",
        headerId = "tasks-heading",
        listAriaLabel = "Tasks"
      )

  case Activities
      extends PtapHomeContentSection(
        containerHeading = "Activities",
        emptyMessage = "There are no activities to show.",
        headerId = "activities-heading",
        listAriaLabel = "Activities"
      )

object PtapHomeContentSection {
  val all: Seq[PtapHomeContentSection] = Seq(Tasks, Activities)
}
