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

/** Tab enumeration for PTAP home page tabs.
  *
  * This enum mirrors the TabEnum from PTAD-135 and will be replaced when PTAD-135 is merged into ptap-feature.
  *
  * PTAD-142 uses this to determine which content section to render.
  */
enum TabEnum(val href: String, val name: String):
  case TASK extends TabEnum("/personal-account/your-tasks", "your-tasks")
  case ACTIVITY extends TabEnum("/personal-account/recent-activity", "recent-activity")
  case TAX extends TabEnum("/personal-account/tax-and-benefits", "tax-and-benefits")
  case NEWS extends TabEnum("/personal-account/hmrc-news", "hmrc-news")
  case SUPPORT extends TabEnum("/personal-account/support", "support")
