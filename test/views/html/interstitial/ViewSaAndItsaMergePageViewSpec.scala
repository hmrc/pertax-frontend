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
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import util.DateTimeTools.{current, previousAndCurrentTaxYear}
import views.html.ViewSpec

class ViewSaAndItsaMergePageViewSpec extends ViewSpec {

  lazy val viewSaAndItsaMergePageView: ViewSaAndItsaMergePageView = inject[ViewSaAndItsaMergePageView]

  lazy implicit val configDecorator: ConfigDecorator            = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  val nextDeadlineTaxYear: String = (current.currentYear + 1).toString
  val saUtr: SaUtr                = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  trait SelfAssessmentLocalSetup {

    val user: SelfAssessmentUser

    implicit val request: UserRequest[AnyContent] = buildUserRequest(
      saUser = user,
      request = request
    )

    def selfAssessmentDoc: Document = asDocument(
      viewSaAndItsaMergePageView(
        nextDeadlineTaxYear,
        isItsa = false,
        isSa = true,
        itsaToggle = true,
        isSeiss = false,
        previousAndCurrentTaxYear,
        user
      ).toString
    )
  }

  "Rendering ViewSaAndItsaMergePageView.scala.html" must {

    "show content for Itsa" in {

      val doc =
        asDocument(
          viewSaAndItsaMergePageView(
            nextDeadlineTaxYear,
            isItsa = true,
            isSa = false,
            itsaToggle = true,
            isSeiss = false,
            previousAndCurrentTaxYear,
            userRequest.saUserType
          ).toString
        )

      doc.text() must include(Messages("label.itsa_header"))
      doc.text() must include(Messages("label.mtd_for_sa"))
      doc.text() must include(Messages("label.send_updates_hmrc_compatible_software"))
      doc.text() must not include Messages("label.from_date_mtd_service_for_itsa_will_replace_sa_tax_return")

      hasLink(
        doc,
        Messages("label.view_manage_your_mtd_for_sa")
      )
    }

    "show content for SA" when {

      "basic content for SA user" in {

        val doc =
          asDocument(
            viewSaAndItsaMergePageView(
              nextDeadlineTaxYear,
              isItsa = false,
              isSa = true,
              itsaToggle = true,
              isSeiss = false,
              previousAndCurrentTaxYear,
              userRequest.saUserType
            ).toString
          )

        doc.text() must include(Messages("label.your_self_assessment"))
        doc.text() must include(
          Messages("label.online_returns_deadline", nextDeadlineTaxYear)
        )
        doc.text() must include(Messages("label.making_tax_digital"))
        doc.text() must include(Messages("label.from_date_mtd_service_for_itsa_will_replace_sa_tax_return"))

        doc
          .getElementsContainingText("Find out about Making Tax Digital for Income Tax Self Assessment")
          .hasAttr("href") mustBe true

        hasLink(
          doc,
          Messages("label.view_manage_sa_return")
        )
      }

      "the user is an Activated SA user" in new SelfAssessmentLocalSetup {

        override val user: SelfAssessmentUser = ActivatedOnlineFilerSelfAssessmentUser(saUtr)

        selfAssessmentDoc.text() must include(Messages("label.your_self_assessment"))
        selfAssessmentDoc.text() must include(
          Messages("label.online_returns_deadline", nextDeadlineTaxYear)
        )

        hasLink(
          selfAssessmentDoc,
          messages("label.view_manage_sa_return")
        )

        hasLink(
          selfAssessmentDoc,
          messages("label.complete_your_tax_return")
        )

        hasLink(
          selfAssessmentDoc,
          messages("label.make_a_payment")
        )

        hasLink(
          selfAssessmentDoc,
          messages("label.view_your_payments")
        )

        hasLink(
          selfAssessmentDoc,
          messages("label.check_if_you_need_to_fill_in_a_tax_return")
        )
      }

      "the user is a Not-yet Activated SA user" in new SelfAssessmentLocalSetup {

        override val user: SelfAssessmentUser = NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr)

        selfAssessmentDoc.text() must include(Messages("label.your_self_assessment"))

        hasLink(
          selfAssessmentDoc,
          messages("label.activate_your_self_assessment")
        )
      }

      "the user is a Wrong Credentials SA user" in new SelfAssessmentLocalSetup {

        override val user: SelfAssessmentUser = WrongCredentialsSelfAssessmentUser(saUtr)

        selfAssessmentDoc.text() must include(Messages("label.your_self_assessment"))

        hasLink(
          selfAssessmentDoc,
          messages("label.find_out_how_to_access_self_assessment")
        )
      }

      "the user is a Not Enrolled SA user" in new SelfAssessmentLocalSetup {

        override val user: SelfAssessmentUser = NotEnrolledSelfAssessmentUser(saUtr)

        selfAssessmentDoc.text() must include(Messages("label.your_self_assessment"))
        selfAssessmentDoc.text() must include(Messages("label.not_enrolled.content"))

        hasLink(
          selfAssessmentDoc,
          messages("label.not_enrolled.link.text")
        )
      }
    }

    "show content for Seiss" in {

      val doc =
        asDocument(
          viewSaAndItsaMergePageView(
            nextDeadlineTaxYear,
            isItsa = false,
            isSa = false,
            itsaToggle = true,
            isSeiss = true,
            previousAndCurrentTaxYear,
            userRequest.saUserType
          ).toString
        )

      doc.text() must include(Messages("label.your_self_assessment"))
      doc.text() must include(Messages("title.seiss"))

      hasLink(
        doc,
        Messages("body.seiss")
      )
    }

    "show content for Itsa , SA , Seiss when all the conditions are true with SA Enrolled" in {

      val doc =
        asDocument(
          viewSaAndItsaMergePageView(
            nextDeadlineTaxYear,
            isItsa = true,
            isSa = true,
            itsaToggle = true,
            isSeiss = true,
            previousAndCurrentTaxYear,
            ActivatedOnlineFilerSelfAssessmentUser(saUtr)
          ).toString
        )

      doc.text() must include(Messages("label.itsa_header"))
      doc.text() must include(Messages("label.mtd_for_sa"))
      doc.text() must include(Messages("label.send_updates_hmrc_compatible_software"))
      doc.text() must include(Messages("label.self_assessment_tax_returns"))
      doc.text() must include(Messages("label.old_way_sa_returns"))

      doc.text() must include(Messages("title.seiss"))

      hasLink(
        doc,
        Messages("label.view_manage_your_mtd_for_sa")
      )

      hasLink(
        doc,
        Messages("label.access_your_sa_returns")
      )

      hasLink(
        doc,
        Messages("body.seiss")
      )
    }

    "show all content besides MTD ITSA message if mongo toggle is false" in {

      val doc =
        asDocument(
          viewSaAndItsaMergePageView(
            nextDeadlineTaxYear,
            isItsa = false,
            isSa = true,
            itsaToggle = false,
            isSeiss = true,
            previousAndCurrentTaxYear,
            userRequest.saUserType
          ).toString
        )

      doc.text() must include(Messages("label.your_self_assessment"))
      doc.text() must not include Messages("label.making_tax_digital")

      hasLink(
        doc,
        Messages("body.seiss")
      )

      hasLink(
        doc,
        Messages("label.view_manage_sa_return")
      )
    }

    "show content for Itsa , SA , Seiss when all the conditions are true with SA not Enrolled" in {

      val doc =
        asDocument(
          viewSaAndItsaMergePageView(
            nextDeadlineTaxYear,
            isItsa = true,
            isSa = true,
            itsaToggle = true,
            isSeiss = true,
            previousAndCurrentTaxYear,
            NotEnrolledSelfAssessmentUser(saUtr)
          ).toString
        )

      doc.text() must include(Messages("label.itsa_header"))
      doc.text() must include(Messages("label.mtd_for_sa"))
      doc.text() must include(Messages("label.send_updates_hmrc_compatible_software"))
      doc.text() must include(Messages("label.self_assessment_tax_returns"))
      doc.text() must include(Messages("label.not_enrolled.content"))
      doc.text() must include(Messages("title.seiss"))
      doc.text() must include(Messages("label.making_tax_digital"))

      hasLink(
        doc,
        Messages("label.view_manage_your_mtd_for_sa")
      )

      hasLink(
        doc,
        messages("label.not_enrolled.link.text")
      )

      hasLink(
        doc,
        Messages("body.seiss")
      )
    }
  }
}
