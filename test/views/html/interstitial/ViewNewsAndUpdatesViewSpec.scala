/*
 * Copyright 2022 HM Revenue & Customs
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
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.i18n.Messages
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class ViewNewsAndUpdatesViewSpec extends ViewSpec {

  lazy val viewNewsAndUpdatesView = injected[ViewNewsAndUpdatesView]

  lazy implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val userRequest = buildUserRequest(request = FakeRequest())

  "Rendering ViewNewsAndUpdatesView.scala.html" must {

    implicit val userRequest = buildUserRequest(request = FakeRequest())

    val doc =
      asDocument(
        viewNewsAndUpdatesView(s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/news").toString
      )
    "show content" in {

      doc.text() must include(Messages("label.news_and_updates"))
      doc.text() must include(Messages("label.percentage_points_uplift_in_NIC"))
      doc.text() must include(
        Messages("label.national_insurance_contributions_will_increase_by_1.25_percentage_points")
      )
      doc.text() must include(Messages("label.the_increase_will_apply_to"))
    }

  }
}
