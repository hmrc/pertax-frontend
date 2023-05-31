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

import config.ConfigDecorator
import models.OverpaidStatus._
import models.UnderpaidStatus._
import models._
import models.admin.FeatureFlag
import viewmodels.Message.text

case class TaxCalculationViewModel(
  taxYears: TaxYears,
  heading: Heading,
  content: List[Message],
  links: List[Link]
)

object TaxCalculationViewModel {

  def fromTaxYearReconciliation(
    reconciliationModel: TaxYearReconciliation,
    taxcalcMakePaymentLinkToggle: FeatureFlag
  )(implicit configDecorator: ConfigDecorator): Option[TaxCalculationViewModel] = {

    val taxYears = TaxYears(reconciliationModel.taxYear, reconciliationModel.taxYear + 1)

    (underpaidViewModel(taxcalcMakePaymentLinkToggle) orElse overpaidViewModel orElse otherViewModels)
      .lift((reconciliationModel.reconciliation, taxYears))
  }

  private def underpaidViewModel(taxcalcMakePaymentLinkToggle: FeatureFlag)(implicit
    configDecorator: ConfigDecorator
  ): PartialFunction[(Reconciliation, TaxYears), TaxCalculationViewModel] = {

    case (Underpaid(_, _, PaidAll), taxYears @ TaxYears(previousTaxYear, currentTaxYear)) =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.you_do_not_owe_any_more_tax", previousTaxYear.toString, currentTaxYear.toString),
          UnderpaidUrl(previousTaxYear)
        ),
        List(text("label.you_have_no_payments_to_make_to_hmrc")),
        Nil
      )

    case (
          status @ Underpaid(Some(amount), Some(dueDate), PaymentDue),
          taxYears @ TaxYears(previousTaxYear, currentTaxYear)
        ) if status.hasDeadlinePassed =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.you_missed_the_deadline_to_pay_your_tax", previousTaxYear.toString, currentTaxYear.toString),
          UnderpaidUrl(previousTaxYear)
        ),
        List(
          text(
            "label.you_owe_hmrc_you_should_have_paid_",
            Literal("%,.2f".format(amount)),
            viewmodels.Date(Some(dueDate))
          )
        ),
        if (taxcalcMakePaymentLinkToggle.isEnabled) {
          List(
            Link(text("label.make_a_payment"), MakePaymentUrl, "You missed the deadline to pay your tax")
          )
        } else {
          Nil
        }
      )

    case (Underpaid(Some(amount), Some(dueDate), PaymentDue), taxYears @ TaxYears(previousTaxYear, currentTaxYear)) =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
          UnderpaidUrl(previousTaxYear)
        ),
        List(
          text("label.you_owe_hmrc_you_must_pay_by_", Literal("%,.2f".format(amount)), viewmodels.Date(Some(dueDate)))
        ),
        (if (taxcalcMakePaymentLinkToggle.isEnabled) {
           List(
             Link(text("label.make_a_payment"), MakePaymentUrl, "Make a payment")
           )
         } else {
           Nil
         }) :+ Link(
          text("label.find_out_why_you_paid_too_little"),
          UnderpaidReasonsUrl(previousTaxYear),
          "Find out why you paid too little"
        )
      )

    case (
          status @ Underpaid(Some(amount), Some(dueDate), PartPaid),
          taxYears @ TaxYears(previousTaxYear, currentTaxYear)
        ) if status.isDeadlineApproaching =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
          UnderpaidUrl(previousTaxYear)
        ),
        List(
          text(
            "label.you_still_owe_hmrc_you_must_pay_by_",
            Literal("%,.2f".format(amount)),
            viewmodels.Date(Some(dueDate))
          )
        ),
        (if (taxcalcMakePaymentLinkToggle.isEnabled) {
           List(
             Link(text("label.make_a_payment"), MakePaymentUrl, "Make a payment")
           )
         } else {
           Nil
         }) :+
          Link(
            text("label.find_out_why_you_paid_too_little"),
            UnderpaidReasonsUrl(previousTaxYear),
            "Find out why you paid too little"
          )
      )

    case (
          status @ Underpaid(Some(amount), Some(dueDate), PartPaid),
          taxYears @ TaxYears(previousTaxYear, currentTaxYear)
        ) if status.hasDeadlinePassed =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.you_missed_the_deadline_to_pay_your_tax", previousTaxYear.toString, currentTaxYear.toString),
          UnderpaidUrl(previousTaxYear)
        ),
        List(
          text(
            "label.you_still_owe_hmrc_you_should_have_paid_",
            Literal("%,.2f".format(amount)),
            viewmodels.Date(Some(dueDate))
          )
        ),
        if (taxcalcMakePaymentLinkToggle.isEnabled) {
          List(
            Link(text("label.make_a_payment"), MakePaymentUrl, "You missed the deadline to pay your tax")
          )
        } else {
          Nil
        }
      )

    case (Underpaid(Some(amount), Some(dueDate), PartPaid), taxYears @ TaxYears(previousTaxYear, currentTaxYear)) =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
          UnderpaidUrl(previousTaxYear)
        ),
        List(
          text(
            "label.you_still_owe_hmrc_you_must_pay_by_",
            Literal("%,.2f".format(amount)),
            viewmodels.Date(Some(dueDate))
          )
        ),
        (if (taxcalcMakePaymentLinkToggle.isEnabled) {
           List(
             Link(text("label.make_a_payment"), MakePaymentUrl, "Make a payment")
           )
         } else {
           Nil
         }) :+
          Link(
            text("label.find_out_why_you_paid_too_little"),
            UnderpaidReasonsUrl(previousTaxYear),
            "Find out why you paid too little"
          )
      )

    case (Underpaid(Some(amount), _, PartPaid | PaymentDue), taxYears @ TaxYears(previousTaxYear, currentTaxYear)) =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
          UnderpaidUrl(previousTaxYear)
        ),
        List(
          text("label.you_owe_hmrc", Literal("%,.2f".format(amount)))
        ),
        (if (taxcalcMakePaymentLinkToggle.isEnabled) {
           List(
             Link(text("label.make_a_payment"), MakePaymentUrl, "Make a payment")
           )
         } else {
           Nil
         }) :+
          Link(
            text("label.find_out_why_you_paid_too_little"),
            UnderpaidReasonsUrl(previousTaxYear),
            "Find out why you paid too little"
          )
      )
  }

  private val overpaidViewModel: PartialFunction[(Reconciliation, TaxYears), TaxCalculationViewModel] = {

    case (Overpaid(Some(amount), Refund), taxYears) =>
      overpaid(
        "label.hmrc_owes_you_a_refund",
        amount,
        taxYears,
        Link(text("label.claim_your_tax_refund"), TaxPaidUrl, "Claim your tax refund")
      )

    case (Overpaid(Some(amount), PaymentProcessing), taxYears) =>
      overpaid("label.hmrc_is_processing_your_refund", amount, taxYears)

    case (Overpaid(Some(amount), PaymentPaid), taxYears) =>
      overpaid("label.hmrc_has_paid_your_refund", amount, taxYears)

    case (Overpaid(Some(amount), ChequeSent), taxYears) =>
      overpaid("label.hmrc_sent_you_a_cheque_for", amount, taxYears)

  }

  private def overpaid(contentKey: String, amount: Double, taxYears: TaxYears, links: Link*) =
    TaxCalculationViewModel(
      taxYears,
      Heading(
        text("label.you_paid_too_much_tax", taxYears.previousTaxYear.toString, taxYears.currentTaxYear.toString),
        OverpaidUrl(taxYears.previousTaxYear)
      ),
      List(text(contentKey, "%,.2f".format(amount))),
      links.toList :+
        Link(
          text("label.find_out_why_you_paid_too_much"),
          OverpaidReasonsUrl(taxYears.previousTaxYear),
          "Find out why you paid too much"
        )
    )

  private val otherViewModels: PartialFunction[(Reconciliation, TaxYears), TaxCalculationViewModel] = {

    case (Balanced | OverpaidTolerance | UnderpaidTolerance, taxYears @ TaxYears(previousTaxYear, currentTaxYear)) =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.tax_year_heading", previousTaxYear.toString, currentTaxYear.toString),
          RightAmountUrl(previousTaxYear)
        ),
        List(
          text("label.you_paid_the_right_amount_of_tax"),
          text("label.nothing_more_to_pay")
        ),
        Nil
      )

    case (BalancedNoEmployment, taxYears @ TaxYears(previousTaxYear, currentTaxYear)) =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.tax_year_heading", previousTaxYear.toString, currentTaxYear.toString),
          NotEmployedUrl(previousTaxYear)
        ),
        List(
          text("label.you_paid_the_right_amount_of_tax"),
          text("label.no_record_of_employment")
        ),
        Nil
      )

    case (NotReconciled, taxYears @ TaxYears(previousTaxYear, currentTaxYear)) =>
      TaxCalculationViewModel(
        taxYears,
        Heading(
          text("label.tax_year_heading", previousTaxYear.toString, currentTaxYear.toString),
          NotCalculatedUrl(previousTaxYear)
        ),
        List(
          text("label.your_tax_has_not_been_calculated"),
          text("label.no_need_to_contact_hmrc")
        ),
        Nil
      )
  }
}
