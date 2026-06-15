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

import models.HmrcCardModel
import play.twirl.api.Html

enum TabEnum(val name: String):
  case Task extends TabEnum("your-tasks")
  case Activity extends TabEnum("recent-activity")
  case Tax extends TabEnum("taxes-and-benefits")
  case News extends TabEnum("hmrc-news")
  case Support extends TabEnum("support")

  def href(): String = this match {
    case Task => "/personal-account"
    case tab  => s"/personal-account/${tab.name}"
  }

final case class PtapNewsAndUpdates(content: Html) extends AnyVal
final case class PtapAlertBanner(content: Html) extends AnyVal

final case class PtapHomeViewModel(
  tasks: Seq[Task],
  newsAndUpdates: Option[PtapNewsAndUpdates],
  showUserResearchBanner: Boolean,
  saUtr: Option[String],
  breathingSpaceIndicator: Boolean,
  alertBannerContent: Option[PtapAlertBanner],
  name: Option[String],
  secondaryNav: SecondaryNavModel,
  currentTab: TabEnum,
  tabCards: Seq[HmrcCardModel]
)
