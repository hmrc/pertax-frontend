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

package viewModels

import models.{ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, NotYetActivatedOnlineFilerSelfAssessmentUser, WrongCredentialsSelfAssessmentUser}
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import util.BaseSpec
import viewmodels.HomeViewModel

class HomeViewModelSpec extends BaseSpec {

  val utr = new SaUtrGenerator().nextSaUtr.utr

  "have no UTR for a non SA user" in {
    val homeViewModel = HomeViewModel(Nil, Nil, Nil, true, NonFilerSelfAssessmentUser)
    homeViewModel shouldBe new HomeViewModel(Nil, Nil, Nil, true, None)
  }

  Seq(
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr(utr)),
    NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr(utr)),
    WrongCredentialsSelfAssessmentUser(SaUtr(utr)),
    NotEnrolledSelfAssessmentUser(SaUtr(utr))
  ).map { saUserType =>
    s"have a UTR for a ${saUserType.toString}" in {
      val homeViewModel = HomeViewModel(Nil, Nil, Nil, true, saUserType)
      homeViewModel shouldBe new HomeViewModel(Nil, Nil, Nil, true, Some(utr))
    }
  }
}
