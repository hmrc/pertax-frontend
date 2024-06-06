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
import controllers.auth.requests.UserRequest
import play.api.i18n.Messages
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class ViewBreathingSpaceViewSpec extends ViewSpec {

  lazy val viewBreathingSpaceView: ViewBreathingSpaceView = inject[ViewBreathingSpaceView]

  lazy implicit val configDecorator: ConfigDecorator            = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  "Rendering ViewBreathingSpaceView.scala.html" must {

    "show content" in {
      val doc =
        asDocument(
          viewBreathingSpaceView().toString
        )

      doc.text() must include(Messages("label.you_are_in_breathing_space"))
      doc.text() must include(Messages("label.add_interest_or_charges_to_your_debt"))
      doc.text() must include(
        Messages("label.contact_you_to_ask_for_payment")
      )
      doc.text() must include(Messages("label.take_enforcement_action_against_you"))
    }
  }
}
