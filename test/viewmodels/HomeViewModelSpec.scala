/*
 * Copyright 2023 HM Revenue & Customs
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

import models._
import testUtils.BaseSpec
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}

class HomeViewModelSpec extends BaseSpec {

  val utr: String = new SaUtrGenerator().nextSaUtr.utr

  "have no UTR for a non SA user" in {
    val homeViewModel = HomeViewModel(
      Nil,
      Nil,
      showUserResearchBanner = true,
      NonFilerSelfAssessmentUser,
      breathingSpaceIndicator = false,
      alertBannerContent = List.empty,
      None
    )
    homeViewModel mustBe new HomeViewModel(Nil, Nil, true, None, false, List.empty, None)
  }

  Seq(
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr(utr)),
    NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr(utr)),
    WrongCredentialsSelfAssessmentUser(SaUtr(utr)),
    NotEnrolledSelfAssessmentUser(SaUtr(utr))
  ).foreach { saUserType =>
//    s"have a UTR for a ${saUserType.toString}" in {
//      val homeViewModel =
//        HomeViewModel(Nil, Nil, Nil, showUserResearchBanner = true, saUserType, breathingSpaceIndicator = true)
//      homeViewModel mustBe new HomeViewModel(Nil, Nil, Nil, true, Some(utr), true)
//    }
    s"have a UTR for a ${saUserType.toString}" in {
      val homeViewModel = HomeViewModel(Nil, Nil, true, saUserType, true, List.empty, None)
      homeViewModel mustBe new HomeViewModel(Nil, Nil, true, Some(utr), true, List.empty, None)
    }
  }
}
