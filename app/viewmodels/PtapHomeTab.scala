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

enum PtapHomeTab(
  val key: String,
  val messageKey: String,
  val contentSection: Option[PtapHomeContentSection]
):
  case Task
      extends PtapHomeTab(
        key = "task",
        messageKey = "ptap.support.uya.p2.sub",
        contentSection = Some(PtapHomeContentSection.Tasks)
      )

  case Activity
      extends PtapHomeTab(
        key = "activity",
        messageKey = "ptap.support.uya.p3.sub",
        contentSection = Some(PtapHomeContentSection.Activities)
      )

  case Tax
      extends PtapHomeTab(
        key = "tax",
        messageKey = "ptap.support.uya.p4.sub",
        contentSection = None
      )

  case News
      extends PtapHomeTab(
        key = "news",
        messageKey = "ptap.support.uya.p5.sub",
        contentSection = None
      )

  case Support
      extends PtapHomeTab(
        key = "support",
        messageKey = "ptap.support.uya.p6.sub",
        contentSection = None
      )

object PtapHomeTab {
  val default: PtapHomeTab = Task
  val all: Seq[PtapHomeTab] = Seq(Task, Activity, Tax, News, Support)

  def fromKey(key: String): PtapHomeTab =
    all.find(_.key == key).getOrElse(default)
}
