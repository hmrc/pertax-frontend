/*
 * Copyright 2020 HM Revenue & Customs
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

import models.{SelfAssessmentUser, SelfAssessmentUserType}
import play.twirl.api.Html

final case class HomeViewModel(
  incomeCards: Seq[Html],
  benefitCards: Seq[Html],
  pensionCards: Seq[Html],
  showUserResearchBanner: Boolean,
  saUtr: Option[String])

object HomeViewModel {
  def apply(
    incomeCards: Seq[Html],
    benefitCards: Seq[Html],
    pensionCards: Seq[Html],
    showUserResearchBanner: Boolean,
    selfAssessmentUserType: SelfAssessmentUserType): HomeViewModel = {

    val utr: Option[String] = selfAssessmentUserType match {
      case saUser: SelfAssessmentUser => Some(saUser.saUtr.toString())
      case _                          => None
    }

    HomeViewModel(incomeCards, benefitCards, pensionCards, showUserResearchBanner, utr)
  }
}
