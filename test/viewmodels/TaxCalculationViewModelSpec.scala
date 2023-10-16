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

package viewmodels

import models.OverpaidStatus.{Unknown => OverpaidUnknown, _}
import models.UnderpaidStatus.{Unknown => UnderpaidUnknown, _}
import models._
import models.admin.TaxcalcMakePaymentLinkToggle
import org.jsoup.nodes.Document
import testUtils.BetterOptionValues
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.play.language.LanguageUtils
import views.html.ViewSpec
import views.html.cards.home.TaxCalculationView

import java.time.LocalDate

class TaxCalculationViewModelSpec extends ViewSpec {

  import BetterOptionValues._

  val taxCalculation = injected[TaxCalculationView]

  def viewWithMakePaymentLink(reconciliation: Reconciliation): Option[Document] =
    TaxCalculationViewModel
      .fromTaxYearReconciliation(
        TaxYearReconciliation(2017, reconciliation),
        FeatureFlag(TaxcalcMakePaymentLinkToggle, true)
      )(config)
      .map { taxRec =>
        asDocument(taxCalculation(taxRec)(messages, config).toString)
      }

  def viewWithoutMakePaymentLink(reconciliation: Reconciliation): Option[Document] =
    TaxCalculationViewModel
      .fromTaxYearReconciliation(
        TaxYearReconciliation(2017, reconciliation),
        FeatureFlag(TaxcalcMakePaymentLinkToggle, false)
      )(config)
      .map { taxRec =>
        asDocument(taxCalculation(taxRec)(messages, config).toString)
      }

  def formatDate(date: LocalDate) =
    injected[LanguageUtils].Dates.formatDate(Some(date), "dd MMMM yyyy")(messages)

  "taxCalculation with payment link toggle" should {

    "not render any content" when {

      "status is BalancedSA" in {
        viewWithMakePaymentLink(BalancedSa) mustBe None
      }

      "status is Underpaid Unknown" in {
        viewWithMakePaymentLink(Underpaid(None, None, UnderpaidUnknown)) mustBe None
      }

      "status is Overpaid Unknown" in {
        viewWithMakePaymentLink(Overpaid(None, OverpaidUnknown)) mustBe None
      }

      "status is Underpaid PaymentsDown" in {
        viewWithMakePaymentLink(Underpaid(None, None, PaymentsDown)) mustBe None
      }

      "status is Missing" in {
        viewWithMakePaymentLink(Missing) mustBe None
      }
    }

    "give the correct heading" when {

      "status is Balanced" in {
        assertContainsLink(
          viewWithMakePaymentLink(Balanced).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.rightAmountUrl(2017)
        )
      }

      "status is OverpaidTolerance" in {
        assertContainsLink(
          viewWithMakePaymentLink(OverpaidTolerance).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.rightAmountUrl(2017)
        )
      }

      "status is underpaidTolerance" in {
        assertContainsLink(
          viewWithMakePaymentLink(UnderpaidTolerance).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.rightAmountUrl(2017)
        )
      }

      "status is Balanced No Employment" in {
        assertContainsLink(
          viewWithMakePaymentLink(BalancedNoEmployment).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.notEmployedUrl(2017)
        )
      }

      "status is Not Reconciled" in {
        assertContainsLink(
          viewWithMakePaymentLink(NotReconciled).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.notCalculatedUrl(2017)
        )
      }

      "status is Underpaid PaidAll" in {

        val status = Underpaid(Some(100.00), None, PaidAll)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_do_not_owe_any_more_tax", "2017", "2018")
        )
      }

      "status is Underpaid PaymentDue" in {

        val status = Underpaid(Some(100.00), None, PaymentDue)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_paid_too_little_tax", "2017", "2018")
        )
      }

      "status is Underpaid PaymentDue and SA deadline has passed" in {

        val status = Underpaid(Some(100.00), Some(LocalDate.now.minusDays(1)), PaymentDue)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_missed_the_deadline_to_pay_your_tax", "2017", "2018")
        )
      }

      "status is Underpaid PartPaid" in {

        val status = Underpaid(Some(100.00), None, PartPaid)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_paid_too_little_tax", "2017", "2018")
        )
      }

      "status is Underpaid PartPaid and SA deadline has passed" in {

        val status = Underpaid(Some(100.00), Some(LocalDate.now.minusDays(1)), PartPaid)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_missed_the_deadline_to_pay_your_tax", "2017", "2018")
        )
      }

      "status is Overpaid Refund" in {

        val status = Overpaid(Some(100.00), Refund)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_paid_too_much_tax", "2017", "2018")
        )
      }

      "status is Overpaid PaymentProcessing" in {

        val status = Overpaid(Some(100.00), PaymentProcessing)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_paid_too_much_tax", "2017", "2018")
        )
      }

      "status is Overpaid PaymentPaid" in {

        val status = Overpaid(Some(100.00), PaymentPaid)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_paid_too_much_tax", "2017", "2018")
        )
      }

      "status is OverpaidPaymentChequeSent" in {

        val status = Overpaid(Some(100.00), ChequeSent)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_paid_too_much_tax", "2017", "2018")
        )
      }
    }

    "display the correct content" when {

      "status is Balanced" in {
        assertContainsText(
          viewWithMakePaymentLink(Balanced).getValue,
          messages("label.you_paid_the_right_amount_of_tax")
        )
        assertContainsText(viewWithMakePaymentLink(Balanced).getValue, messages("label.nothing_more_to_pay"))
      }

      "status is OverpaidTolerance" in {
        assertContainsText(
          viewWithMakePaymentLink(OverpaidTolerance).getValue,
          messages("label.you_paid_the_right_amount_of_tax")
        )
        assertContainsText(viewWithMakePaymentLink(OverpaidTolerance).getValue, messages("label.nothing_more_to_pay"))
      }

      "status is UnderpaidTolerance" in {
        assertContainsText(
          viewWithMakePaymentLink(UnderpaidTolerance).getValue,
          messages("label.you_paid_the_right_amount_of_tax")
        )
        assertContainsText(viewWithMakePaymentLink(UnderpaidTolerance).getValue, messages("label.nothing_more_to_pay"))
      }

      "status is Balanced No Employment" in {
        assertContainsText(
          viewWithMakePaymentLink(BalancedNoEmployment).getValue,
          messages("label.you_paid_the_right_amount_of_tax")
        )
        assertContainsText(
          viewWithMakePaymentLink(BalancedNoEmployment).getValue,
          messages("label.no_record_of_employment")
        )
      }

      "status is Not Reconciled" in {
        assertContainsText(
          viewWithMakePaymentLink(NotReconciled).getValue,
          messages("label.your_tax_has_not_been_calculated")
        )
        assertContainsText(viewWithMakePaymentLink(NotReconciled).getValue, messages("label.no_need_to_contact_hmrc"))
      }

      "status is UnderpaidPaymentDue" in {

        val status = Underpaid(Some(100.00), None, PaymentDue)

        assertContainsText(viewWithMakePaymentLink(status).getValue, messages("label.you_owe_hmrc", "100.00"))
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is UnderpaidPaymentDue with due date" in {

        val date = LocalDate.now.plusDays(31)

        val status = Underpaid(Some(100.00), Some(date), PaymentDue)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}")
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PaymentDue with due date and sa date approaching" in {

        val date = LocalDate.now.plusDays(29)

        val status = Underpaid(Some(100.00), Some(date), PaymentDue)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}")
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PaymentDue with due date and sa date has passed" in {

        val date = LocalDate.now.minusDays(1)

        val status = Underpaid(Some(100.00), Some(date), PaymentDue)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_owe_hmrc_you_should_have_paid_", "100.00", s"${formatDate(date)}")
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
      }

      "status is Underpaid PartPaid" in {

        val status = Underpaid(Some(100.00), None, PartPaid)

        assertContainsText(viewWithMakePaymentLink(status).getValue, messages("label.you_owe_hmrc", "100.00"))
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PartPaid with due date" in {

        val date = LocalDate.now.plusDays(31)

        val status = Underpaid(Some(100.00), Some(date), PartPaid)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_still_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}")
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PartPaid with due date and sa date approaching" in {

        val date = LocalDate.now.plusDays(29)

        val status = Underpaid(Some(100.00), Some(date), PartPaid)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_paid_too_little_tax", "2017", "2018")
        )
        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_still_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}")
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PartPaid with due date and sa date has passed" in {

        val date = LocalDate.now.minusDays(1)

        val status = Underpaid(Some(100.00), Some(date), PartPaid)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_still_owe_hmrc_you_should_have_paid_", "100.00", s"${formatDate(date)}")
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
      }

      "status is Underpaid PaidAll" in {

        val status = Underpaid(Some(100.00), None, PaidAll)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.you_have_no_payments_to_make_to_hmrc")
        )
      }

      "status is Overpaid Refund" in {

        val status = Overpaid(Some(100.00), Refund)

        assertContainsText(viewWithMakePaymentLink(status).getValue, messages("label.hmrc_owes_you_a_refund", "100.00"))
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.claim_your_tax_refund"),
          config.taxPaidUrl
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_much"),
          config.overpaidUrlReasons(2017)
        )
      }

      "status is Overpaid PaymentProcessing" in {

        val status = Overpaid(Some(100.00), PaymentProcessing)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.hmrc_is_processing_your_refund", "100.00")
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_much"),
          config.overpaidUrlReasons(2017)
        )
      }

      "status is Overpaid PaymentPaid" in {

        val status = Overpaid(Some(100.00), PaymentPaid)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.hmrc_has_paid_your_refund", "100.00")
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_much"),
          config.overpaidUrlReasons(2017)
        )
      }

      "status is Overpaid PaymentChequeSent" in {

        val status = Overpaid(Some(100.00), ChequeSent)

        assertContainsText(
          viewWithMakePaymentLink(status).getValue,
          messages("label.hmrc_sent_you_a_cheque_for", "100.00")
        )
        assertContainsLink(
          viewWithMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_much"),
          config.overpaidUrlReasons(2017)
        )
      }
    }
  }

  "taxCalculation without payment link toggle" should {

    "not render any content" when {

      "status is BalancedSA" in {
        viewWithoutMakePaymentLink(BalancedSa) mustBe None
      }

      "status is Underpaid Unknown" in {
        viewWithoutMakePaymentLink(Underpaid(None, None, UnderpaidUnknown)) mustBe None
      }

      "status is Overpaid Unknown" in {
        viewWithoutMakePaymentLink(Overpaid(None, OverpaidUnknown)) mustBe None
      }

      "status is Underpaid PaymentsDown" in {
        viewWithoutMakePaymentLink(Underpaid(None, None, PaymentsDown)) mustBe None
      }

      "status is Missing" in {
        viewWithoutMakePaymentLink(Missing) mustBe None
      }
    }

    "give the correct heading" when {

      "status is Balanced" in {
        assertContainsLink(
          viewWithoutMakePaymentLink(Balanced).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.rightAmountUrl(2017)
        )
      }

      "status is OverpaidTolerance" in {
        assertContainsLink(
          viewWithoutMakePaymentLink(OverpaidTolerance).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.rightAmountUrl(2017)
        )
      }

      "status is underpaidTolerance" in {
        assertContainsLink(
          viewWithoutMakePaymentLink(UnderpaidTolerance).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.rightAmountUrl(2017)
        )
      }

      "status is Balanced No Employment" in {
        assertContainsLink(
          viewWithoutMakePaymentLink(BalancedNoEmployment).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.notEmployedUrl(2017)
        )
      }

      "status is Not Reconciled" in {
        assertContainsLink(
          viewWithoutMakePaymentLink(NotReconciled).getValue,
          messages("label.tax_year_heading", "2017", "2018"),
          config.notCalculatedUrl(2017)
        )
      }

      "status is Underpaid PaidAll" in {

        val status = Underpaid(Some(100.00), None, PaidAll)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_do_not_owe_any_more_tax", "2017", "2018")
        )
      }

      "status is Underpaid PaymentDue" in {

        val status = Underpaid(Some(100.00), None, PaymentDue)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_paid_too_little_tax", "2017", "2018")
        )
      }

      "status is Underpaid PaymentDue and SA deadline has passed" in {

        val status = Underpaid(Some(100.00), Some(LocalDate.now.minusDays(1)), PaymentDue)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_missed_the_deadline_to_pay_your_tax", "2017", "2018")
        )
      }

      "status is Underpaid PartPaid" in {

        val status = Underpaid(Some(100.00), None, PartPaid)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_paid_too_little_tax", "2017", "2018")
        )
      }

      "status is Underpaid PartPaid and SA deadline has passed" in {

        val status = Underpaid(Some(100.00), Some(LocalDate.now.minusDays(1)), PartPaid)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_missed_the_deadline_to_pay_your_tax", "2017", "2018")
        )
      }

      "status is Overpaid Refund" in {

        val status = Overpaid(Some(100.00), Refund)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_paid_too_much_tax", "2017", "2018")
        )
      }

      "status is Overpaid PaymentProcessing" in {

        val status = Overpaid(Some(100.00), PaymentProcessing)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_paid_too_much_tax", "2017", "2018")
        )
      }

      "status is Overpaid PaymentPaid" in {

        val status = Overpaid(Some(100.00), PaymentPaid)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_paid_too_much_tax", "2017", "2018")
        )
      }

      "status is OverpaidPaymentChequeSent" in {

        val status = Overpaid(Some(100.00), ChequeSent)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_paid_too_much_tax", "2017", "2018")
        )
      }
    }

    "display the correct content" when {

      "status is Balanced" in {
        assertContainsText(
          viewWithoutMakePaymentLink(Balanced).getValue,
          messages("label.you_paid_the_right_amount_of_tax")
        )
        assertContainsText(viewWithoutMakePaymentLink(Balanced).getValue, messages("label.nothing_more_to_pay"))
      }

      "status is OverpaidTolerance" in {
        assertContainsText(
          viewWithoutMakePaymentLink(OverpaidTolerance).getValue,
          messages("label.you_paid_the_right_amount_of_tax")
        )
        assertContainsText(
          viewWithoutMakePaymentLink(OverpaidTolerance).getValue,
          messages("label.nothing_more_to_pay")
        )
      }

      "status is UnderpaidTolerance" in {
        assertContainsText(
          viewWithoutMakePaymentLink(UnderpaidTolerance).getValue,
          messages("label.you_paid_the_right_amount_of_tax")
        )
        assertContainsText(
          viewWithoutMakePaymentLink(UnderpaidTolerance).getValue,
          messages("label.nothing_more_to_pay")
        )
      }

      "status is Balanced No Employment" in {
        assertContainsText(
          viewWithoutMakePaymentLink(BalancedNoEmployment).getValue,
          messages("label.you_paid_the_right_amount_of_tax")
        )
        assertContainsText(
          viewWithoutMakePaymentLink(BalancedNoEmployment).getValue,
          messages("label.no_record_of_employment")
        )
      }

      "status is Not Reconciled" in {
        assertContainsText(
          viewWithoutMakePaymentLink(NotReconciled).getValue,
          messages("label.your_tax_has_not_been_calculated")
        )
        assertContainsText(
          viewWithoutMakePaymentLink(NotReconciled).getValue,
          messages("label.no_need_to_contact_hmrc")
        )
      }

      "status is UnderpaidPaymentDue" in {

        val status = Underpaid(Some(100.00), None, PaymentDue)

        assertContainsText(viewWithoutMakePaymentLink(status).getValue, messages("label.you_owe_hmrc", "100.00"))
        assertNotContainLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is UnderpaidPaymentDue with due date" in {

        val date = LocalDate.now.plusDays(31)

        val status = Underpaid(Some(100.00), Some(date), PaymentDue)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}")
        )
        assertNotContainLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PaymentDue with due date and sa date approaching" in {

        val date = LocalDate.now.plusDays(29)

        val status = Underpaid(Some(100.00), Some(date), PaymentDue)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}")
        )
        assertNotContainLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PaymentDue with due date and sa date has passed" in {

        val date = LocalDate.now.minusDays(1)

        val status = Underpaid(Some(100.00), Some(date), PaymentDue)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_owe_hmrc_you_should_have_paid_", "100.00", s"${formatDate(date)}")
        )
        assertNotContainLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
      }

      "status is Underpaid PartPaid" in {

        val status = Underpaid(Some(100.00), None, PartPaid)

        assertContainsText(viewWithoutMakePaymentLink(status).getValue, messages("label.you_owe_hmrc", "100.00"))
        assertNotContainLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PartPaid with due date" in {

        val date = LocalDate.now.plusDays(31)

        val status = Underpaid(Some(100.00), Some(date), PartPaid)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_still_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}")
        )
        assertNotContainLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PartPaid with due date and sa date approaching" in {

        val date = LocalDate.now.plusDays(29)

        val status = Underpaid(Some(100.00), Some(date), PartPaid)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_paid_too_little_tax", "2017", "2018")
        )
        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_still_owe_hmrc_you_must_pay_by_", "100.00", s"${formatDate(date)}")
        )
        assertNotContainLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_little"),
          config.underpaidUrlReasons(2017)
        )
      }

      "status is Underpaid PartPaid with due date and sa date has passed" in {

        val date = LocalDate.now.minusDays(1)

        val status = Underpaid(Some(100.00), Some(date), PartPaid)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_still_owe_hmrc_you_should_have_paid_", "100.00", s"${formatDate(date)}")
        )
        assertNotContainLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.make_a_payment"),
          config.makePaymentUrl
        )
      }

      "status is Underpaid PaidAll" in {

        val status = Underpaid(Some(100.00), None, PaidAll)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.you_have_no_payments_to_make_to_hmrc")
        )
      }

      "status is Overpaid Refund" in {

        val status = Overpaid(Some(100.00), Refund)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.hmrc_owes_you_a_refund", "100.00")
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.claim_your_tax_refund"),
          config.taxPaidUrl
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_much"),
          config.overpaidUrlReasons(2017)
        )
      }

      "status is Overpaid PaymentProcessing" in {

        val status = Overpaid(Some(100.00), PaymentProcessing)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.hmrc_is_processing_your_refund", "100.00")
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_much"),
          config.overpaidUrlReasons(2017)
        )
      }

      "status is Overpaid PaymentPaid" in {

        val status = Overpaid(Some(100.00), PaymentPaid)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.hmrc_has_paid_your_refund", "100.00")
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_much"),
          config.overpaidUrlReasons(2017)
        )
      }

      "status is Overpaid PaymentChequeSent" in {

        val status = Overpaid(Some(100.00), ChequeSent)

        assertContainsText(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.hmrc_sent_you_a_cheque_for", "100.00")
        )
        assertContainsLink(
          viewWithoutMakePaymentLink(status).getValue,
          messages("label.find_out_why_you_paid_too_much"),
          config.overpaidUrlReasons(2017)
        )
      }
    }
  }
}
