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
import models._
import org.jsoup.nodes.Document
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, AnyContentAsEmpty}
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class ViewHICBCChargeInPAYEViewSpec extends ViewSpec {

  lazy val viewHICBCChargeInPAYEView: ViewHICBCChargeInPAYEView =
    inject[ViewHICBCChargeInPAYEView]

  lazy implicit val configDecorator: ConfigDecorator            = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

  trait SelfAssessmentLocalSetup {

    val user: SelfAssessmentUser

    implicit val request: UserRequest[AnyContent] = buildUserRequest(
      saUser = user,
      request = FakeRequest()
    )

    def selfAssessmentDoc: Document = asDocument(
      viewHICBCChargeInPAYEView().toString
    )
  }

  "Rendering ViewHICBCChargeInPAYEView.scala.html" must {

    "show content for page" in {
      val doc  = asDocument(
        viewHICBCChargeInPAYEView().toString
      )
      val text = doc.text()

      text must include(Messages("label.view_hicbc_taxfree"))
      text must include(Messages("label.hicbc_paye"))
      text must include(Messages("label.hicbc_deduction_tax_free"))

      doc.getElementById("continue").text() mustBe Messages("label.continue_hicbc_taxfree")

    }

  }
}
