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

package models

import config.ConfigDecorator
import play.api.i18n.Messages
import models.OverpaidStatus.{Unknown => OverpaidUnknown, _}
import models.UnderpaidStatus._
import util.LanguageHelper

case class TaxCalculationViewModel(
                                  heading: Heading,
                                  content: List[String],
                                  links: List[Link]
                                  )

object TaxCalculationViewModel {
  def apply(reconciliationModel: TaxYearReconciliations, previousTaxYear: Int, currentTaxYear: Int)(implicit configDecorator: ConfigDecorator, messages: Messages): Option[TaxCalculationViewModel] = {

    val underpaidUrlReasons = s"${configDecorator.taxCalcFrontendHost}/tax-you-paid/$previousTaxYear-$currentTaxYear/paid-too-little/reasons"
    val overpaidUrlReasons = s"${configDecorator.taxCalcFrontendHost}/tax-you-paid/$previousTaxYear-$currentTaxYear/paid-too-much/reasons"

    val underpaidUrl = s"${configDecorator.taxCalcFrontendHost}/tax-you-paid/$previousTaxYear-$currentTaxYear/paid-too-little"
    val overpaidUrl = s"${configDecorator.taxCalcFrontendHost}/tax-you-paid/$previousTaxYear-$currentTaxYear/paid-too-much"

    val rightAmountUrl = s"${configDecorator.taxCalcFrontendHost}/tax-you-paid/$previousTaxYear-$currentTaxYear/right-amount"
    val notEmployedUrl = s"${configDecorator.taxCalcFrontendHost}/tax-you-paid/$previousTaxYear-$currentTaxYear/not-employed"
    val notCalculatedUrl = s"${configDecorator.taxCalcFrontendHost}/tax-you-paid/$previousTaxYear-$currentTaxYear/not-yet-calculated"

    val makePaymentUrl = "https://www.gov.uk/simple-assessment"

    def overpaidHeading = Heading(
      Messages("label.you_paid_too_much_tax", previousTaxYear.toString, currentTaxYear.toString),
      overpaidUrl
    )

    reconciliationModel.reconciliation match {

      case Balanced =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.tax_year_heading", previousTaxYear.toString, currentTaxYear.toString),
            rightAmountUrl
          ),
          List(
            Messages("label.you_paid_the_right_amount_of_tax"),
            Messages("label.nothing_more_to_pay")
          ),
          Nil
        ))


      case BalancedNoEmployment =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.tax_year_heading", previousTaxYear.toString, currentTaxYear.toString),
            notEmployedUrl
          ),
          List(
            Messages("label.you_paid_the_right_amount_of_tax"),
            Messages("label.no_record_of_employment")
          ),
          Nil
        ))

      case NotReconciled =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.tax_year_heading", previousTaxYear.toString, currentTaxYear.toString),
            notCalculatedUrl
          ),
          List(
            Messages("label.your_tax_has_not_been_calculated"),
            Messages("label.no_need_to_contact_hmrc")
          ),
          Nil
        ))

      case Underpaid(_, _, PaidAll) =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.you_do_not_owe_any_more_tax", previousTaxYear.toString, currentTaxYear.toString),
            underpaidUrl
          ),
          List(Messages("label.you_have_no_payments_to_make_to_hmrc")),
          Nil
        ))

      case status @ Underpaid(Some(amount), Some(dueDate), PaymentDue) if status.isDeadlineApproaching =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
            underpaidUrl
          ),
          List(
            Messages("label.you_owe_hmrc_you_must_pay_by_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy"))
          ),
          List(
            Link(Messages("label.make_a_payment"), makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons, "Find out why you paid too little")
          )
        ))

      case status @ Underpaid(Some(amount), Some(dueDate), PaymentDue) if status.hasDeadlinePassed =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.you_missed_the_deadline_to_pay_your_tax", previousTaxYear.toString, currentTaxYear.toString),
            underpaidUrl
          ),
          List(
            Messages("label.you_owe_hmrc_you_should_have_paid_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy")
            )),
          List(
            Link(Messages("label.make_a_payment"), makePaymentUrl, "You missed the deadline to pay your tax")
          )
        ))

      case Underpaid(Some(amount), Some(dueDate), PaymentDue) =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
            underpaidUrl
          ),
          List(
            Messages("label.you_owe_hmrc_you_must_pay_by_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy"))
          ),
          List(
            Link(Messages("label.make_a_payment"), makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons, "Find out why you paid too little")
          )
        ))

      case Underpaid(Some(amount), _, PaymentDue) =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
            underpaidUrl
          ),
          List(
            Messages("label.you_owe_hmrc", "%,.2f".format(amount))
          ),
          List(
            Link(Messages("label.make_a_payment"), makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons, "Find out why you paid too little")
          )
        ))

      case status @ Underpaid(Some(amount), Some(dueDate), PartPaid) if status.isDeadlineApproaching =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.you_still_owe_hmrc_you_must_pay_by_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy")
            ),
            ""
          ), 
          Nil,
          List(
            Link(Messages("label.make_a_payment"), makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons, "Find out why you paid too little")
          )
        ))

      case status @ Underpaid(Some(amount), Some(dueDate), PartPaid) if status.hasDeadlinePassed =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.you_missed_the_deadline_to_pay_your_tax", previousTaxYear.toString, currentTaxYear.toString),
            underpaidUrl
          ),
          List(
            Messages("label.you_still_owe_hmrc_you_should_have_paid_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy"))
          ),
          List(
            Link(Messages("label.make_a_payment"), makePaymentUrl, "You missed the deadline to pay your tax")
          )
        ))

      case Underpaid(Some(amount), Some(dueDate), PartPaid) =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
            underpaidUrl
          ),
          List(
            Messages("label.you_still_owe_hmrc_you_must_pay_by_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy")),
            underpaidUrl
          ),
          List(
            Link(Messages("label.make_a_payment"), makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons, "Find out why you paid too little")
          )
        ))

      case Underpaid(Some(amount), _, PartPaid) =>
        Some(TaxCalculationViewModel(
          Heading(
            Messages("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
            underpaidUrl
          ),
          List(
            Messages("label.you_owe_hmrc", "%,.2f".format(amount))
          ),
          List(
            Link(Messages("label.make_a_payment"), makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), underpaidUrlReasons, "Find out why you paid too little")
          )
        ))


      case Overpaid(_, OverpaidUnknown) => None

      case Overpaid(Some(amount), Refund) =>
        Some(TaxCalculationViewModel(
          overpaidHeading,
          List(Messages("label.hmrc_owes_you_a_refund", "%,.2f".format(amount))),
          List(
            Link(Messages("label.claim_your_tax_refund"), s"${configDecorator.taxCalcFrontendHost}/tax-you-paid/status", "Claim your tax refund"),
            Link(Messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons, "Find out why you paid too much")
          )
        ))

      case Overpaid(Some(amount), PaymentProcessing) =>
        Some(TaxCalculationViewModel(
          overpaidHeading,
          List(Messages("label.hmrc_is_processing_your_refund", "%,.2f".format(amount))),
          List(
            Link(Messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons, "Find out why you paid too much")
          )
        ))

      case Overpaid(Some(amount), PaymentPaid) =>
        Some(TaxCalculationViewModel(
          overpaidHeading,
          List(Messages("label.hmrc_has_paid_your_refund", "%,.2f".format(amount))),
          List(
            Link(Messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons, "Find out why you paid too much")
          )
        ))

      case Overpaid(Some(amount), ChequeSent) =>
        Some(TaxCalculationViewModel(
          overpaidHeading,
          List(Messages("label.hmrc_sent_you_a_cheque_for", "%,.2f".format(amount))),
          List(
            Link(Messages("label.find_out_why_you_paid_too_much"), overpaidUrlReasons, "Find out why you paid too much")
          )
        ))

      case _ => None
    }
  }
}

case class Heading(label: String, url: String)

case class Link(message: String, url: String, gaLabel: String)
