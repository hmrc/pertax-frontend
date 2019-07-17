/*
 * Copyright 2019 HM Revenue & Customs
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

import models._
import org.joda.time.LocalDate
import org.jsoup.nodes.Document
import views.html.cards.home.taxCalculation

class TaxCalculationViewSpec extends ViewSpec {

  def view(state: TaxCalculationState): Document = asDocument(
    taxCalculation(state, 2017, 2018)(messages, pertaxContext).toString
  )

  val underpaidUrlReasons: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/paid-too-little/reasons"
  val overpaidUrlReasons: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/paid-too-much/reasons"

  val underpaidUrl: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/paid-too-little"
  val overpaidUrl: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/paid-too-much"

  val makePaymentUrl: String = "https://www.gov.uk/simple-assessment"

  val taxcalcUrl = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/status"

  "taxCalculation" should {

    "give the correct heading" when {

      "state is TaxCalculationUnderpaidPaidAllState" in {

        val state = TaxCalculationUnderpaidPaidAllState(2017, 2018, None)

        assertContainsText(view(state), messages("label.you_do_not_owe_any_more_tax", "2017", "2018"))
      }

      "state is TaxCalculationUnderpaidPaymentDueState" in {

        val state = TaxCalculationUnderpaidPaymentDueState(10000, 2017, 2018, None, None)

        assertContainsText(view(state), messages("label.you_paid_too_little_tax", "2017", "2018"))
      }

      "state is TaxCalculationUnderpaidPaymentDueState and SA deadline has passed" in {

        val state = TaxCalculationUnderpaidPaymentDueState(10000, 2017, 2018, Some(LocalDate.now), Some(SaDeadlinePassedStatus))

        assertContainsText(view(state), messages("label.you_missed_the_deadline_to_pay_your_tax", "2017", "2018"))
      }

      "state is TaxCalculationUnderpaidPartPaidState" in {

        val state = TaxCalculationUnderpaidPartPaidState(10000, 2017, 2018, None, None)

        assertContainsText(view(state), messages("label.you_paid_too_little_tax", "2017", "2018"))
      }

      "state is TaxCalculationUnderpaidPartPaidState and SA deadline has passed" in {

        val state = TaxCalculationUnderpaidPartPaidState(10000, 2017, 2018, Some(LocalDate.now), Some(SaDeadlinePassedStatus))

        assertContainsText(view(state), messages("label.you_missed_the_deadline_to_pay_your_tax", "2017", "2018"))
      }

      "state is TaxCalculationOverpaidRefundState" in {

        val state = TaxCalculationOverpaidRefundState(10000, 2017, 2018)

        assertContainsText(view(state), messages("label.you_paid_too_much_tax", "2017", "2018"))
      }

      "state is TaxCalculationOverpaidPaymentProcessingState" in {

        val state = TaxCalculationOverpaidPaymentProcessingState(10000)

        assertContainsText(view(state), messages("label.you_paid_too_much_tax", "2017", "2018"))
      }

      "state is TaxCalculationOverpaidPaymentPaidState" in {

        val state = TaxCalculationOverpaidPaymentPaidState(10000, Some(LocalDate.now))

        assertContainsText(view(state), messages("label.you_paid_too_much_tax", "2017", "2018"))
      }

      "state is TaxCalculationOverpaidPaymentChequeSentState" in {

        val state = TaxCalculationOverpaidPaymentChequeSentState(10000, Some(LocalDate.now))

        assertContainsText(view(state), messages("label.you_paid_too_much_tax", "2017", "2018"))
      }
    }

    "display the correct content" when {

      "state is TaxCalculationUnderpaidPaymentDueState" in {

        val state = TaxCalculationUnderpaidPaymentDueState(10000, 2017, 2018, None, None)

        assertContainsText(view(state), messages("label.you_owe_hmrc", "10,000.00"))
        assertContainsLink(view(state), messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "state is TaxCalculationUnderpaidPaymentDueState with due date" in {

        val state = TaxCalculationUnderpaidPaymentDueState(10000, 2017, 2018, Some(LocalDate.parse("2019-01-01")), None)

        assertContainsText(view(state), messages("label.you_owe_hmrc_you_must_pay_by_", "10,000.00", "1 January 2019"))
        assertContainsLink(view(state), messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "state is TaxCalculationUnderpaidPaymentDueState with due date and sa date approaching" in {

        val state = TaxCalculationUnderpaidPaymentDueState(10000, 2017, 2018, Some(LocalDate.parse("2019-01-01")), Some(SaDeadlineApproachingStatus))

        assertContainsText(view(state), messages("label.you_owe_hmrc_you_must_pay_by_", "10,000.00", "1 January 2019"))
        assertContainsLink(view(state), messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "state is TaxCalculationUnderpaidPaymentDueState with due date and sa date has passed" in {

        val state = TaxCalculationUnderpaidPaymentDueState(10000, 2017, 2018, Some(LocalDate.parse("2019-01-01")), Some(SaDeadlinePassedStatus))

        assertContainsText(view(state), messages("label.you_owe_hmrc_you_should_have_paid_", "10,000.00", "1 January 2019"))
        assertContainsLink(view(state), messages("label.make_a_payment"), makePaymentUrl)
      }

      "state is TaxCalculationUnderpaidPartPaidState" in {

        val state = TaxCalculationUnderpaidPartPaidState(10000, 2017, 2018, None, None)

        assertContainsText(view(state), messages("label.you_owe_hmrc", "10,000.00"))
        assertContainsLink(view(state), messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "state is TaxCalculationUnderpaidPartPaidState with due date" in {

        val state = TaxCalculationUnderpaidPartPaidState(10000, 2017, 2018, Some(LocalDate.parse("2019-01-01")), None)

        assertContainsText(view(state), messages("label.you_still_owe_hmrc_you_must_pay_by_", "10,000.00", "1 January 2019"))
        assertContainsLink(view(state), messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "state is TaxCalculationUnderpaidPartPaidState with due date and sa date approaching" in {

        val state = TaxCalculationUnderpaidPartPaidState(10000, 2017, 2018, Some(LocalDate.parse("2019-01-01")), Some(SaDeadlineApproachingStatus))

        assertContainsText(view(state), messages("label.you_still_owe_hmrc_you_must_pay_by_", "10,000.00", "1 January 2019"))
        assertContainsLink(view(state), messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "state is TaxCalculationUnderpaidPartPaidState with due date and sa date has passed" in {

        val state = TaxCalculationUnderpaidPartPaidState(10000, 2017, 2018, Some(LocalDate.parse("2019-01-01")), Some(SaDeadlinePassedStatus))

        assertContainsText(view(state), messages("label.you_still_owe_hmrc_you_should_have_paid_", "10,000.00", "1 January 2019"))
        assertContainsLink(view(state), messages("label.make_a_payment"), makePaymentUrl)
      }

      "state is TaxCalculationUnderpaidPaidAllState" in {

        val state = TaxCalculationUnderpaidPaidAllState(2017, 2018, None)

        assertContainsText(view(state), messages("label.you_have_no_payments_to_make_to_hmrc"))
      }

      "state is TaxCalculationOverpaidRefundState" in {

        val state = TaxCalculationOverpaidRefundState(10000, 2017, 2018)

        assertContainsText(view(state), messages("label.hmrc_owes_you_a_refund", "10,000.00"))
        assertContainsLink(view(state), messages("label.claim_your_tax_refund"), taxcalcUrl)
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons)
      }

      "state is TaxCalculationOverpaidPaymentProcessingState" in {

        val state = TaxCalculationOverpaidPaymentProcessingState(10000)

        assertContainsText(view(state), messages("label.hmrc_is_processing_your_refund", "10,000.00"))
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons)
      }

      "state is TaxCalculationOverpaidPaymentPaidState" in {

        val state = TaxCalculationOverpaidPaymentPaidState(10000, Some(LocalDate.now))

        assertContainsText(view(state), messages("label.hmrc_has_paid_your_refund", "10,000.00"))
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons)
      }

      "state is TaxCalculationOverpaidPaymentChequeSentState" in {

        val state = TaxCalculationOverpaidPaymentChequeSentState(10000, Some(LocalDate.now))

        assertContainsText(view(state), messages("label.hmrc_sent_you_a_cheque_for", "10,000.00"))
        assertContainsLink(view(state), messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons)
      }

    }
  }
}
