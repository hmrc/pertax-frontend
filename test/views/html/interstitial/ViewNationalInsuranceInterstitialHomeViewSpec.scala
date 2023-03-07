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

package views.html.interstitial

import config.ConfigDecorator
import play.api.test.FakeRequest
import play.twirl.api.Html
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class ViewNationalInsuranceInterstitialHomeViewSpec extends ViewSpec {

  lazy val view = injected[ViewNationalInsuranceInterstitialHomeView]

  lazy implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val userRequest                           = buildUserRequest(request = FakeRequest())

  "Rendering ViewNationalInsuranceInterstitialHomeView.scala.html" must {

    "show NINO section when a nino is present" in {
      val document = asDocument(view(Html(""), "asfa", userRequest.nino).toString)
      Option(document.select(".nino").first).isDefined mustBe true
      document.body().toString must include(messages("label.check_your_national_insurance_contributions"))
      document.body().toString must include(
        messages("label.see_a_record_of_the_national_insurance_contributions_which_count_towards_")
      )
    }

    "show incomplete when there is no NINO" in {
      val document = asDocument(view(Html(""), "http://google.com", None).toString)
      Option(document.select(".nino").first).isDefined mustBe false
      document.body().toString must include(messages("label.you_can_see_this_part_of_your_account_if_you_complete"))
    }

  }
}
