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
import models.UnderpaidStatus.{Unknown => UnderpaidUnknown, _}
import models.OverpaidStatus.{Unknown => OverpaidUnknown, _}
import org.scalatest.OptionValues
import util.{BetterOptionValues, LanguageHelper}

class TaxCalculationViewSpec extends ViewSpec {

  import BetterOptionValues._

  def view(reconciliation: Reconciliation): Option[Document] =
    TaxCalculationViewModel.fromTaxYearReconciliations(TaxYearReconciliations(2017, reconciliation))(config, messages).map { taxRec =>
      asDocument(taxCalculation(taxRec)(messages, pertaxContext, config).toString)
    }

  def formatDate(date: LocalDate) = LanguageHelper.langUtils.Dates.formatDate(Some(date), "dd MMMM yyyy")(messages)

  val underpaidUrlReasons: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/paid-too-little/reasons"
  val overpaidUrlReasons: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/paid-too-much/reasons"

  val underpaidUrl: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/paid-too-little"
  val overpaidUrl: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/paid-too-much"

  val makePaymentUrl: String = "https://www.gov.uk/simple-assessment"

  val rightAmountUrl: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/right-amount"
  val notEmployedUrl: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/not-employed"
  val notCalculatedUrl: String = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/2017-2018/not-yet-calculated"

  val taxcalcUrl = s"${pertaxContext.configDecorator.taxCalcFrontendHost}/tax-you-paid/status"

  "taxCalculation" should {

    "not render any content" when {

      "status is BalancedSA" in {
        view(BalancedSa) shouldBe None
      }

      "status is Underpaid Unknown" in {
        view(Underpaid(None, None, UnderpaidUnknown)) shouldBe None
      }

      "status is Overpaid Unknown" in {
        view(Overpaid(None, OverpaidUnknown)) shouldBe None
      }

      "status is Underpaid PaymentsDown" in {
        view(Underpaid(None, None, PaymentsDown)) shouldBe None
      }
    }

    "give the correct heading" when {

      "status is Balanced" in {
        assertContainsLink(view(Balanced).getValue, messages("label.tax_year_heading", "2017", "2018"), rightAmountUrl)
      }

      "status is Balanced No Employment" in {
        assertContainsLink(view(BalancedNoEmployment).getValue, messages("label.tax_year_heading", "2017", "2018"), notEmployedUrl)
      }

      "status is Not Reconciled" in {
        assertContainsLink(view(NotReconciled).getValue, messages("label.tax_year_heading", "2017", "2018"), notCalculatedUrl)
      }

      "status is Underpaid PaidAll" in {

        val status = Underpaid(Some(100.00), None, PaidAll)

        assertContainsText(view(status).getValue, messages("label.you_do_not_owe_any_more_tax", "2017", "2018"))
      }

      "status is Underpaid PaymentDue" in {

        val status = Underpaid(Some(100.00), None, PaymentDue)

        assertContainsText(view(status).getValue, messages("label.you_paid_too_little_tax", "2017", "2018"))
      }

      "status is Underpaid PaymentDue and SA deadline has passed" in {

        val status = Underpaid(Some(100.00), Some(LocalDate.now.minusDays(1)), PaymentDue)

        assertContainsText(view(status).getValue, messages("label.you_missed_the_deadline_to_pay_your_tax", "2017", "2018"))
      }

      "status is Underpaid PartPaid" in {

        val status = Underpaid(Some(100.00), None, PartPaid)

        assertContainsText(view(status).getValue, messages("label.you_paid_too_little_tax", "2017", "2018"))
      }

      "status is Underpaid PartPaid and SA deadline has passed" in {

        val status = Underpaid(Some(100.00), Some(LocalDate.now.minusDays(1)), PartPaid)

        assertContainsText(view(status).getValue, messages("label.you_missed_the_deadline_to_pay_your_tax", "2017", "2018"))
      }

      "status is Overpaid Refund" in {

        val status = Overpaid(Some(100.00), Refund)

        assertContainsText(view(status).getValue, messages("label.you_paid_too_much_tax", "2017", "2018"))
      }

      "status is Overpaid PaymentProcessing" in {

        val status = Overpaid(Some(100.00), PaymentProcessing)

        assertContainsText(view(status).getValue, messages("label.you_paid_too_much_tax", "2017", "2018"))
      }

      "status is Overpaid PaymentPaid" in {

        val status = Overpaid(Some(100.00), PaymentPaid)

        assertContainsText(view(status).getValue, messages("label.you_paid_too_much_tax", "2017", "2018"))
      }

      "status is OverpaidPaymentChequeSent" in {

        val status = Overpaid(Some(100.00), ChequeSent)

        assertContainsText(view(status).getValue, messages("label.you_paid_too_much_tax", "2017", "2018"))
      }
    }

    "display the correct content" when {

      "status is Balanced" in {
        assertContainsText(view(Balanced).getValue, messages("label.you_paid_the_right_amount_of_tax"))
        assertContainsText(view(Balanced).getValue, messages("label.nothing_more_to_pay"))
      }

      "status is Balanced No Employment" in {
        assertContainsText(view(BalancedNoEmployment).getValue, messages("label.you_paid_the_right_amount_of_tax"))
        assertContainsText(view(BalancedNoEmployment).getValue, messages("label.no_record_of_employment"))
      }

      "status is Not Reconciled" in {
        assertContainsText(view(NotReconciled).getValue, messages("label.your_tax_has_not_been_calculated"))
        assertContainsText(view(NotReconciled).getValue, messages("label.no_need_to_contact_hmrc"))
      }

      "status is UnderpaidPaymentDue" in {

        val status = Underpaid(Some(100.00), None, PaymentDue)

        assertContainsText(view(status).getValue, messages("label.you_owe_hmrc", "100.00"))
        assertContainsLink(view(status).getValue, messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "status is UnderpaidPaymentDue with due date" in {

        val date = LocalDate.now.plusDays(31)

        val status = Underpaid(Some(100.00), Some(date), PaymentDue)

        assertContainsText(view(status).getValue, messages("label.you_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}"))
        assertContainsLink(view(status).getValue, messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "status is Underpaid PaymentDue with due date and sa date approaching" in {

        val date = LocalDate.now.plusDays(29)

        val status = Underpaid(Some(100.00), Some(date), PaymentDue)

        assertContainsText(view(status).getValue, messages("label.you_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}"))
        assertContainsLink(view(status).getValue, messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "status is Underpaid PaymentDue with due date and sa date has passed" in {

        val date = LocalDate.now.minusDays(1)

        val status = Underpaid(Some(100.00), Some(date), PaymentDue)

        assertContainsText(view(status).getValue, messages("label.you_owe_hmrc_you_should_have_paid_", "100.00", s"${formatDate(date)}"))
        assertContainsLink(view(status).getValue, messages("label.make_a_payment"), makePaymentUrl)
      }

      "status is Underpaid PartPaid" in {

        val status = Underpaid(Some(100.00), None, PartPaid)

        assertContainsText(view(status).getValue, messages("label.you_owe_hmrc", "100.00"))
        assertContainsLink(view(status).getValue, messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "status is Underpaid PartPaid with due date" in {

        val date = LocalDate.now.plusDays(31)

        val status = Underpaid(Some(100.00), Some(date), PartPaid)

        assertContainsText(view(status).getValue, messages("label.you_still_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}"))
        assertContainsLink(view(status).getValue, messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "status is Underpaid PartPaid with due date and sa date approaching" in {

        val date = LocalDate.now.plusDays(29)

        val status = Underpaid(Some(100.00), Some(date), PartPaid)

        assertContainsText(view(status).getValue, messages("label.you_still_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}"))
        assertContainsLink(view(status).getValue, messages("label.make_a_payment"), makePaymentUrl)
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons)
      }

      "status is Underpaid PartPaid with due date and sa date has passed" in {

        val date = LocalDate.now.minusDays(1)

        val status = Underpaid(Some(100.00), Some(date), PartPaid)

        assertContainsText(view(status).getValue, messages("label.you_still_owe_hmrc_you_should_have_paid_", "100.00", s"${formatDate(date)}"))
        assertContainsLink(view(status).getValue, messages("label.make_a_payment"), makePaymentUrl)
      }

      "status is Underpaid PaidAll" in {

        val status = Underpaid(Some(100.00), None, PaidAll)

        assertContainsText(view(status).getValue, messages("label.you_have_no_payments_to_make_to_hmrc"))
      }

      "status is Overpaid Refund" in {

        val status = Overpaid(Some(100.00), Refund)

        assertContainsText(view(status).getValue, messages("label.hmrc_owes_you_a_refund", "100.00"))
        assertContainsLink(view(status).getValue, messages("label.claim_your_tax_refund"), taxcalcUrl)
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons)
      }

      "status is Overpaid PaymentProcessing" in {

        val status = Overpaid(Some(100.00), PaymentProcessing)

        assertContainsText(view(status).getValue, messages("label.hmrc_is_processing_your_refund", "100.00"))
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons)
      }

      "status is Overpaid PaymentPaid" in {

        val status = Overpaid(Some(100.00), PaymentPaid)

        assertContainsText(view(status).getValue, messages("label.hmrc_has_paid_your_refund", "100.00"))
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons)
      }

      "status is Overpaid PaymentChequeSent" in {

        val status = Overpaid(Some(100.00), ChequeSent)

        assertContainsText(view(status).getValue, messages("label.hmrc_sent_you_a_cheque_for", "100.00"))
        assertContainsLink(view(status).getValue, messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons)
      }

    }
  }
}
