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

package views.html.cards

import java.net.URLDecoder

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, NotYetActivatedOnlineFilerSelfAssessmentUser, SelfAssessmentUser, WrongCredentialsSelfAssessmentUser}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.i18n.{Lang, Messages}
import uk.gov.hmrc.domain.SaUtr
import util.BaseSpec
import util.UserRequestFixture.buildUserRequest
import views.html.cards.home.selfAssessment

import scala.collection.JavaConverters._

class SelfAssessmentViewSpec extends BaseSpec {

  val lang = Lang("en")
  implicit val messages = Messages(lang, Messages.Implicits.applicationMessagesApi)
  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]

  def hasLink(document: Document, content: String, href: String)(implicit messages: Messages): Assertion = {
    val link = document
      .getElementsMatchingText(content)
      .eachAttr("href")
      .asScala
      .head

    URLDecoder.decode(link, "UTF-8") should include(href)
  }

  val saUtr = SaUtr("123456789")

  trait LocalSetup {

    val user: SelfAssessmentUser

    implicit val request: UserRequest[_] = buildUserRequest(
      saUser = user,
      request = request
    )

    val thisYear = "2019"
    val nextYear = "2020"

    def doc: Document = Jsoup.parse(
      selfAssessment(user, thisYear, nextYear).toString
    )
  }

  "Self Assessment Card" should {

    "render the correct card link" when {

      "the user is an Activated SA user" in new LocalSetup {

        override val user: SelfAssessmentUser = ActivatedOnlineFilerSelfAssessmentUser(saUtr)

        hasLink(
          doc,
          messages("label.self_assessment"),
          "/personal-account/self-assessment-summary"
        )
      }

      "the user is not an Activated SA user" in new LocalSetup {

        override val user: SelfAssessmentUser = NotEnrolledSelfAssessmentUser(saUtr)

        hasLink(
          doc,
          messages("label.self_assessment"),
          "/personal-account/self-assessment"
        )
      }
    }

    "render the correct content" when {

      "the user is an Activated SA user" in new LocalSetup {

        override val user: SelfAssessmentUser = ActivatedOnlineFilerSelfAssessmentUser(saUtr)

        doc.text() should include(
          messages("label.view_and_manage_your_self_assessment_tax_return_the_deadline_for_online_", nextYear)
        )

        hasLink(
          doc,
          messages("label.complete_your_tax_return"),
          s"/self-assessment-file/$thisYear/ind/${user.saUtr}/return?lang=eng"
        )

        hasLink(
          doc,
          messages("label.make_a_payment"),
          "/personal-account/self-assessment/make-payment"
        )

        hasLink(
          doc,
          messages("label.view_your_payments"),
          s"/self-assessment/ind/${user.saUtr}/account/payments"
        )

        hasLink(
          doc,
          messages("label.check_if_you_need_to_fill_in_a_tax_return"),
          "https://www.gov.uk/check-if-you-need-a-tax-return"
        )
      }

      "the user is a Not-yet Activated SA user" in new LocalSetup {

        override val user: SelfAssessmentUser = NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr)

        hasLink(
          doc,
          messages("label.activate_your_self_assessment"),
          "/personal-account/self-assessment"
        )
      }

      "the user is a Wrong Credentials SA user" in new LocalSetup {

        override val user: SelfAssessmentUser = WrongCredentialsSelfAssessmentUser(saUtr)

        hasLink(
          doc,
          messages("label.find_out_how_to_access_self_assessment"),
          "/personal-account/self-assessment"
        )
      }

      "the user is a Not Enrolled SA user" in new LocalSetup {

        override val user: SelfAssessmentUser = NotEnrolledSelfAssessmentUser(saUtr)

        hasLink(
          doc,
          messages("label.find_out_how_to_access_self_assessment"),
          "/personal-account/self-assessment"
        )
      }
    }
  }
}
