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
import controllers.auth.requests.UserRequest
import models._
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import util.DateTimeTools.{current, previousAndCurrentTaxYear}
import util.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class ViewItsaInterstitialHomeViewSpec extends ViewSpec {

  lazy val viewItsaInterstitialHomeView = injected[ViewItsaInterstitialHomeView]

  lazy implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val userRequest = buildUserRequest(request = FakeRequest())

  def hasLink(document: Document, content: String, href: String)(implicit messages: Messages): Assertion =
    document.getElementsMatchingText(content).hasAttr("href") mustBe true

  val currentTaxYear = current.currentYear.toString
  val currentTaxYearMinusOne = current.previous.currentYear.toString
  val currentTaxYearMinusTwo = current.previous.previous.currentYear.toString
  val saUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  trait SelfAssessmentLocalSetup {

    val user: SelfAssessmentUser

    implicit val request: UserRequest[AnyContent] = buildUserRequest(
      saUser = user,
      request = request
    )

    def selfAssessmentDoc: Document = asDocument(
      viewItsaInterstitialHomeView(
        s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home",
        currentTaxYear,
        currentTaxYearMinusOne,
        currentTaxYearMinusTwo,
        false,
        true,
        false,
        previousAndCurrentTaxYear,
        user
      ).toString
    )
  }

  "Rendering ViewItsaInterstitialHomeView.scala.html" must {

    "show content for Itsa" in {

      val doc =
        asDocument(
          viewItsaInterstitialHomeView(
            s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home",
            currentTaxYear,
            currentTaxYearMinusOne,
            currentTaxYearMinusTwo,
            true,
            false,
            false,
            previousAndCurrentTaxYear,
            userRequest.saUserType
          ).toString
        )

      doc.text() must include(Messages("label.your_self_assessment"))
      doc.text() must include(Messages("label.current_tax_year_range", currentTaxYearMinusOne, currentTaxYear))
      doc.text() must include(Messages("label.making_tax_digital"))
      hasLink(
        doc,
        Messages("label.view_and_manage_your_mtd_for_itsa"),
        s"${configDecorator.itsaViewUrl}"
      )
    }

    "show content for SA" when {

      "basic content for SA user" in {

        val doc =
          asDocument(
            viewItsaInterstitialHomeView(
              s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home",
              currentTaxYear,
              currentTaxYearMinusOne,
              currentTaxYearMinusTwo,
              false,
              true,
              false,
              previousAndCurrentTaxYear,
              userRequest.saUserType
            ).toString
          )

        doc.text() must include(Messages("label.your_self_assessment"))
        doc.text() must include(
          Messages("label.previous_tax_year_range", currentTaxYearMinusTwo, currentTaxYearMinusOne)
        )

        hasLink(
          doc,
          Messages("label.view_and_manage_your_earlier_self_assessment_years"),
          "/personal-account/self-assessment-summary"
        )
      }

      "the user is an Activated SA user" in new SelfAssessmentLocalSetup {

        override val user: SelfAssessmentUser = ActivatedOnlineFilerSelfAssessmentUser(saUtr)

        selfAssessmentDoc.text() must include(Messages("label.your_self_assessment"))
        selfAssessmentDoc.text() must include(
          Messages("label.previous_tax_year_range", currentTaxYearMinusTwo, currentTaxYearMinusOne)
        )
        selfAssessmentDoc.text() must include(Messages("label.view_and_manage_your_earlier_self_assessment_years"))

        hasLink(
          selfAssessmentDoc,
          messages("label.self_assessment"),
          "/personal-account/self-assessment-summary"
        )

        hasLink(
          selfAssessmentDoc,
          messages("label.complete_your_tax_return"),
          s"/self-assessment-file/$previousAndCurrentTaxYear/ind/$saUtr/return?lang=eng"
        )

        hasLink(
          selfAssessmentDoc,
          messages("label.make_a_payment"),
          "/personal-account/self-assessment/make-payment"
        )

        hasLink(
          selfAssessmentDoc,
          messages("label.view_your_payments"),
          s"/self-assessment/ind/$saUtr/account/payments"
        )

        hasLink(
          selfAssessmentDoc,
          messages("label.check_if_you_need_to_fill_in_a_tax_return"),
          "https://www.gov.uk/check-if-you-need-a-tax-return"
        )
      }

      "the user is not an Activated SA user" in new SelfAssessmentLocalSetup {

        override val user: SelfAssessmentUser = NotEnrolledSelfAssessmentUser(saUtr)

        selfAssessmentDoc.text() must include(Messages("label.your_self_assessment"))
        selfAssessmentDoc.text() must include(Messages("label.not_enrolled.link.text"))

        hasLink(
          selfAssessmentDoc,
          messages("label.self_assessment"),
          "/personal-account/sa-enrolment"
        )
      }

      "the user is a Not-yet Activated SA user" in new SelfAssessmentLocalSetup {

        override val user: SelfAssessmentUser = NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr)

        selfAssessmentDoc.text() must include(Messages("label.your_self_assessment"))
        selfAssessmentDoc.text() must include(Messages("label.activate_your_self_assessment"))

        hasLink(
          selfAssessmentDoc,
          messages("label.activate_your_self_assessment"),
          "/personal-account/self-assessment"
        )
      }

      "the user is a Wrong Credentials SA user" in new SelfAssessmentLocalSetup {

        override val user: SelfAssessmentUser = WrongCredentialsSelfAssessmentUser(saUtr)

        selfAssessmentDoc.text() must include(Messages("label.your_self_assessment"))
        selfAssessmentDoc.text() must include(Messages("label.find_out_how_to_access_self_assessment"))

        hasLink(
          selfAssessmentDoc,
          messages("label.find_out_how_to_access_self_assessment"),
          "/personal-account/self-assessment"
        )
      }

      "the user is a Not Enrolled SA user" in new SelfAssessmentLocalSetup {

        override val user: SelfAssessmentUser = NotEnrolledSelfAssessmentUser(saUtr)

        selfAssessmentDoc.text() must include(Messages("label.your_self_assessment"))
        selfAssessmentDoc.text() must include(Messages("label.not_enrolled.link.text"))

        hasLink(
          selfAssessmentDoc,
          messages("label.not_enrolled.link.text"),
          "/personal-account/sa-enrolment"
        )
      }
    }

    "show content for Seiss" in {

      val doc =
        asDocument(
          viewItsaInterstitialHomeView(
            s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home",
            currentTaxYear,
            currentTaxYearMinusOne,
            currentTaxYearMinusTwo,
            false,
            false,
            true,
            previousAndCurrentTaxYear,
            userRequest.saUserType
          ).toString
        )

      doc.text() must include(Messages("label.your_self_assessment"))
      doc.text() must include(Messages("title.seiss"))

      hasLink(
        doc,
        Messages("body.seiss"),
        s"${configDecorator.seissClaimsUrl}"
      )
    }

    "show content for Itsa , SA , Seiss when all the conditions are true" in {

      val doc =
        asDocument(
          viewItsaInterstitialHomeView(
            s"${configDecorator.pertaxFrontendHomeUrl}/personal-account/self-assessment-home",
            currentTaxYear,
            currentTaxYearMinusOne,
            currentTaxYearMinusTwo,
            true,
            true,
            true,
            previousAndCurrentTaxYear,
            userRequest.saUserType
          ).toString
        )

      doc.text() must include(Messages("label.your_self_assessment"))
      doc.text() must include(Messages("title.seiss"))
      doc.text() must include(Messages("label.current_tax_year_range", currentTaxYearMinusOne, currentTaxYear))
      doc.text() must include(Messages("label.previous_tax_year_range", currentTaxYearMinusTwo, currentTaxYearMinusOne))

      hasLink(
        doc,
        Messages("label.view_and_manage_your_earlier_self_assessment_years"),
        "/personal-account/self-assessment-summary"
      )
      hasLink(
        doc,
        Messages("body.seiss"),
        s"${configDecorator.seissClaimsUrl}"
      )
    }
  }
}
