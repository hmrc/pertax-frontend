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
import org.scalatest.Assertion
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, AnyContentAsEmpty}
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class ViewChildBenefitsSummarySingleAccountInterstitialViewSpec extends ViewSpec {

  lazy val viewChildBenefitsSummarySingleAccountInterstitialView
    : ViewChildBenefitsSummarySingleAccountInterstitialView =
    injected[ViewChildBenefitsSummarySingleAccountInterstitialView]

  lazy implicit val configDecorator: ConfigDecorator            = injected[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

  trait SelfAssessmentLocalSetup {

    val user: SelfAssessmentUser

    implicit val request: UserRequest[AnyContent] = buildUserRequest(
      saUser = user,
      request = request
    )

    def selfAssessmentDoc: Document = asDocument(
      viewChildBenefitsSummarySingleAccountInterstitialView(
        s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home"
      ).toString
    )
  }

  "Rendering ViewChildBenefitsSummarySingleAccountInterstitialView.scala.html" must {

    "show content for Child Benefit Feature for Single Sign On" in {

      val doc =
        asDocument(
          viewChildBenefitsSummarySingleAccountInterstitialView(
            s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home"
          ).toString
        )

      doc.text() must include(Messages("label.make_or_manage_a_child_benefit_claim"))
      doc.text() must include(Messages("label.make_a_claim"))

      hasLink(
        doc,
        Messages("label.report_changes_that_affect_your_child_benefit")
      )

      hasLink(
        doc,
        Messages("label.view_your_child_benefit_payment_history")
      )

      hasLink(
        doc,
        Messages("label.view_your_proof_of_entitlement_to_child_benefit")
      )

      hasLink(
        doc,
        Messages("label.high_income_child_benefit_charge")
      )
    }

    "show incomplete when there is no NINO" in {

      implicit val userRequest: UserRequest[AnyContent] = buildUserRequest(
        nino = None,
        request = FakeRequest()
      )

      val doc =
        asDocument(
          viewChildBenefitsSummarySingleAccountInterstitialView(
            s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home"
          ).toString
        )
      Option(doc.select(".nino").first).isDefined mustBe false
      doc.body().toString must include(messages("label.you_can_see_this_part_of_your_account_if_you_complete"))
    }
  }
}
