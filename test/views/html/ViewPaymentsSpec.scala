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

package views.html

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.ActivatedOnlineFilerSelfAssessmentUser
import org.joda.time.LocalDate
import org.jsoup.nodes.Document
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, LocalPartialRetriever}
import util.UserRequestFixture.buildUserRequest
import viewmodels.SelfAssessmentPayment
import views.html.selfassessment.viewPayments
import util.Fixtures._

class ViewPaymentsSpec extends ViewSpec with BaseSpec {

  val user = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr))
  val messagesImplicit = messages
  lazy val templateRenderer = injected[TemplateRenderer]
  lazy val partialRetriever = injected[LocalPartialRetriever]

  lazy val request: UserRequest[_] = buildUserRequest(
    saUser = user,
    request = buildFakeRequestWithAuth("GET")
  )

  def doc(payments: List[SelfAssessmentPayment], request: UserRequest[_] = request): Document =
    asDocument(
      viewPayments(payments)(request, config, partialRetriever, templateRenderer, messages).toString
    )

  val noPayments = List[SelfAssessmentPayment]()

  val payments = List(
    SelfAssessmentPayment(LocalDate.now().minusDays(12), "KT123456", 103.05),
    SelfAssessmentPayment(LocalDate.now().minusDays(59), "KT123457", 361.85),
    SelfAssessmentPayment(LocalDate.now().minusDays(61), "KT123458", 7.00)
  )

  "viewPayments" when {

    "the page is rendered" should {

      "render the correct h1" in {
        assertContainsText(doc(noPayments), messages("title.selfAssessment.viewPayments.h1"))
        assertContainsText(doc(payments), messages("title.selfAssessment.viewPayments.h1"))
      }

      "render the correct title" in {
        assertContainsText(doc(noPayments), request.retrievedName.get.toString)
        assertContainsText(doc(payments), request.retrievedName.get.toString)
      }

      "render the correct name" in {
        val unnamedRequest = buildUserRequest(
          saUser = user,
          request = request,
          userName = None
        )
        assertContainsText(doc(noPayments, unnamedRequest), messages("label.your_account"))
        assertContainsText(doc(payments, unnamedRequest), messages("label.your_account"))
      }
    }
    "no payments are present" should {

      "not render the payments table if no payments have been made in the last 60 days" in {
        doc(noPayments).getElementsByTag("table") shouldBe empty
      }

      "show no payments content if no payments have been made in the last 60 days" in {
        assertContainsText(doc(noPayments), messages("label.selfAssessment.noPaymentsIn60"))
      }
    }

    "payments are present" should {

      "show correct advisory" in {
        assertContainsText(doc(payments), messages("label.selfAssessment.balanceUpdateAdvisory"))
      }

      "show correct table headings" in {

        val docWithPayments = doc(payments)

        assertContainsText(docWithPayments, messages("label.selfAssessment.paymentsTable.date"))
        assertContainsText(docWithPayments, messages("label.selfAssessment.paymentsTable.reference"))
        assertContainsText(docWithPayments, messages("label.selfAssessment.paymentsTable.amount"))
      }

      "show correct number of payments in the payment table" in {
        assert(doc(payments).select("tr td.payment_date").size == payments.length)
      }
    }
  }
}
