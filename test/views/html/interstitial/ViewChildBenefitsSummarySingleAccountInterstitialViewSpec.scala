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
    inject[ViewChildBenefitsSummarySingleAccountInterstitialView]

  lazy implicit val configDecorator: ConfigDecorator            = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

  trait SelfAssessmentLocalSetup {

    val user: SelfAssessmentUser

    implicit val request: UserRequest[AnyContent] = buildUserRequest(
      saUser = user,
      request = FakeRequest()
    )

    def selfAssessmentDoc: Document = asDocument(
      viewChildBenefitsSummarySingleAccountInterstitialView(false).toString
    )
  }

  "Rendering ViewChildBenefitsSummarySingleAccountInterstitialView.scala.html" must {

    def childBenefitSingleAccountInterstitialView(doc: Document): Assertion = {
      doc.text() must include(Messages("label.child_benefit"))
      doc.text() must include(Messages("label.making_a_claim"))

      hasLink(
        doc,
        Messages("label.report_changes_that_affect_your_child_benefit")
      )

      hasLink(
        doc,
        Messages("label.guidance_for_when_your_child_turns_sixteen")
      )

      hasLink(
        doc,
        Messages("label.extend_your_payment_while_your_child_stays_in_education")
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
        Messages("label.change_your_bank_details")
      )
    }

    "show content for Child Benefit Feature for Single Sign On when not registered for HICBC charge" in {
      val doc = asDocument(
        viewChildBenefitsSummarySingleAccountInterstitialView(isRegisteredForHICBCWithCharge = false).toString
      )
      behave like childBenefitSingleAccountInterstitialView(doc)

      doc.getElementById("hicbc").text() mustBe Messages("label.high_income_child_benefit_charge")

      hasLink(
        doc,
        Messages("label.visit_high_income_child_benefit_charge")
      )

      notHasLink(
        doc,
        Messages("label.view_hicbc_taxfree")
      )

      val text = doc.text()
      text must include(Messages("label.hicbc_li1"))
      text must include(Messages("label.hicbc_li2"))
      text must include(Messages("label.hicbc_li3"))
    }
    "show content for Child Benefit Feature for Single Sign On when registered for HICBC charge" in {
      val doc = asDocument(
        viewChildBenefitsSummarySingleAccountInterstitialView(isRegisteredForHICBCWithCharge = true).toString
      )
      behave like childBenefitSingleAccountInterstitialView(doc)

      hasLink(
        doc,
        Messages("label.view_hicbc_taxfree")
      )

      val text = doc.text()
      text mustNot include(Messages("label.hicbc_li1"))
      text mustNot include(Messages("label.hicbc_li2"))
      text mustNot include(Messages("label.hicbc_li3"))
    }
  }
}
