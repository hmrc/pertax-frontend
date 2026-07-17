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

import models.{MyService, OtherService}
import views.html.components.TabDefaultInsetText
import play.api.i18n.Messages
import play.twirl.api.Html

enum TabEnum(val name: String):
  case Task extends TabEnum("your-tasks")
  case Activity extends TabEnum("recent-activity")
  case Tax extends TabEnum("taxes-and-benefits")
  case News extends TabEnum("hmrc-news")
  case Support extends TabEnum("support")

  def href(ptap: Option[String] = None): String                              = {
    val queryString = ptap.fold("")(v => s"?ptap=$v")
    this match {
      case Task => s"/personal-account$queryString"
      case tab  => s"/personal-account/${tab.name}$queryString"
    }
  }
  def defaultInset(query: Option[String])(implicit messages: Messages): Html =
    TabDefaultInsetText(this, query)
  def cardContainerHeading(implicit messages: Messages): Option[String]      =
    this match
      case Task     => Some(messages("ptap.support.uya.p2.sub"))
      case Activity => Some(messages("ptap.support.uya.p3.sub"))
      case _        => None

final case class PtapAlertBanner(content: Html) extends AnyVal

final case class PtapHomeViewModel(
  showUserResearchBanner: Boolean,
  saUtr: Option[String],
  breathingSpaceIndicator: Boolean,
  alertBannerContent: Option[PtapAlertBanner],
  name: Option[String],
  secondaryNav: SecondaryNavModel,
  tabContent: List[CardContainerModel],
  showNewsAndUpdatesView: Boolean = false,
  showSupportView: Boolean = false,
  showTaxesAndBenefitsView: Boolean = false,
  myServices: Seq[MyService] = Seq.empty,
  otherServices: Seq[OtherService] = Seq.empty
)
