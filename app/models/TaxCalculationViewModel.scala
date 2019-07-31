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
                                    taxYears: TaxYears,
                                    heading: Heading,
                                    content: List[String],
                                    links: List[Link]
                                  )

object TaxCalculationViewModel {

  def fromTaxYearReconciliations(reconciliationModel: TaxYearReconciliations)(implicit configDecorator: ConfigDecorator, messages: Messages): Option[TaxCalculationViewModel] = {

    val previousTaxYear = reconciliationModel.taxYear
    val currentTaxYear = reconciliationModel.taxYear + 1
    val taxYears = TaxYears(previousTaxYear, currentTaxYear)

    def overpaid(contentKey: String, amount: Double, links: Link*)=
      TaxCalculationViewModel(
        taxYears,
        Heading(
          Messages("label.you_paid_too_much_tax", previousTaxYear.toString, currentTaxYear.toString),
          configDecorator.overpaidUrl(previousTaxYear)
        ),
        List(Messages(contentKey, "%,.2f".format(amount))),
        links.toList :+
          Link(Messages("label.find_out_why_you_paid_too_much"), configDecorator.overpaidUrlReasons(previousTaxYear), "Find out why you paid too much")

      )

    reconciliationModel.reconciliation match {

      case Balanced =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.tax_year_heading", previousTaxYear.toString, currentTaxYear.toString),
            configDecorator.rightAmountUrl(previousTaxYear)
          ),
          List(
            Messages("label.you_paid_the_right_amount_of_tax"),
            Messages("label.nothing_more_to_pay")
          ),
          Nil
        ))

      case BalancedNoEmployment =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.tax_year_heading", previousTaxYear.toString, currentTaxYear.toString),
            configDecorator.notEmployedUrl(previousTaxYear)
          ),
          List(
            Messages("label.you_paid_the_right_amount_of_tax"),
            Messages("label.no_record_of_employment")
          ),
          Nil
        ))

      case NotReconciled =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.tax_year_heading", previousTaxYear.toString, currentTaxYear.toString),
            configDecorator.notCalculatedUrl(previousTaxYear)
          ),
          List(
            Messages("label.your_tax_has_not_been_calculated"),
            Messages("label.no_need_to_contact_hmrc")
          ),
          Nil
        ))

      case Underpaid(_, _, PaidAll) =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.you_do_not_owe_any_more_tax", previousTaxYear.toString, currentTaxYear.toString),
            configDecorator.underpaidUrl(previousTaxYear)
          ),
          List(Messages("label.you_have_no_payments_to_make_to_hmrc")),
          Nil
        ))

      case status @ Underpaid(Some(amount), Some(dueDate), PaymentDue) if status.hasDeadlinePassed =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.you_missed_the_deadline_to_pay_your_tax", previousTaxYear.toString, currentTaxYear.toString),
            configDecorator.underpaidUrl(previousTaxYear)
          ),
          List(
            Messages("label.you_owe_hmrc_you_should_have_paid_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy")
            )),
          List(
            Link(Messages("label.make_a_payment"), configDecorator.makePaymentUrl, "You missed the deadline to pay your tax")
          )
        ))

      case Underpaid(Some(amount), Some(dueDate), PaymentDue) =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
            configDecorator.underpaidUrl(previousTaxYear)
          ),
          List(
            Messages("label.you_owe_hmrc_you_must_pay_by_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy"))
          ),
          List(
            Link(Messages("label.make_a_payment"), configDecorator.makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), configDecorator.underpaidUrlReasons(previousTaxYear), "Find out why you paid too little")
          )
        ))

      case status @ Underpaid(Some(amount), Some(dueDate), PartPaid) if status.isDeadlineApproaching =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.you_still_owe_hmrc_you_must_pay_by_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy")
            ),
            ""
          ),
          Nil,
          List(
            Link(Messages("label.make_a_payment"), configDecorator.makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), configDecorator.underpaidUrlReasons(previousTaxYear), "Find out why you paid too little")
          )
        ))

      case status @ Underpaid(Some(amount), Some(dueDate), PartPaid) if status.hasDeadlinePassed =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.you_missed_the_deadline_to_pay_your_tax", previousTaxYear.toString, currentTaxYear.toString),
            configDecorator.underpaidUrl(previousTaxYear)
          ),
          List(
            Messages("label.you_still_owe_hmrc_you_should_have_paid_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy"))
          ),
          List(
            Link(Messages("label.make_a_payment"), configDecorator.makePaymentUrl, "You missed the deadline to pay your tax")
          )
        ))

      case Underpaid(Some(amount), Some(dueDate), PartPaid) =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
            configDecorator.underpaidUrl(previousTaxYear)
          ),
          List(
            Messages("label.you_still_owe_hmrc_you_must_pay_by_", "%,.2f".format(amount), LanguageHelper.langUtils.Dates.formatDate(Some(dueDate), "dd MMMM yyyy"))
          ),
          List(
            Link(Messages("label.make_a_payment"), configDecorator.makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), configDecorator.underpaidUrlReasons(previousTaxYear), "Find out why you paid too little")
          )
        ))

      case Underpaid(Some(amount), _, PartPaid | PaymentDue) =>
        Some(TaxCalculationViewModel(
          taxYears,
          Heading(
            Messages("label.you_paid_too_little_tax", previousTaxYear.toString, currentTaxYear.toString),
            configDecorator.underpaidUrl(previousTaxYear)
          ),
          List(
            Messages("label.you_owe_hmrc", "%,.2f".format(amount))
          ),
          List(
            Link(Messages("label.make_a_payment"), configDecorator.makePaymentUrl, "Make a payment"),
            Link(Messages("label.find_out_why_you_paid_too_little"), configDecorator.underpaidUrlReasons(previousTaxYear), "Find out why you paid too little")
          )
        ))

      case Overpaid(_, OverpaidUnknown) => None

      case Overpaid(Some(amount), Refund) =>
        Some(overpaid(
          "label.hmrc_owes_you_a_refund",
          amount,
          Link(Messages("label.claim_your_tax_refund"), s"${configDecorator.taxCalcFrontendHost}/tax-you-paid/status", "Claim your tax refund")
        ))

      case Overpaid(Some(amount), PaymentProcessing) =>
        Some(overpaid("label.hmrc_is_processing_your_refund", amount))

      case Overpaid(Some(amount), PaymentPaid) =>
        Some(overpaid("label.hmrc_has_paid_your_refund", amount))

      case Overpaid(Some(amount), ChequeSent) =>
        Some(overpaid("label.hmrc_sent_you_a_cheque_for", amount))

      case _ => None
    }
  }
}

case class Heading(label: String, url: String)

case class Link(message: String, url: String, gaLabel: String)

case class TaxYears(previousTaxYear: Int, currentTaxYear: Int)