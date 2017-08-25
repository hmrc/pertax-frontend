/*
 * Copyright 2017 HM Revenue & Customs
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

sealed trait TaxCalculationState
sealed trait TaxCalculationOverpaidState extends TaxCalculationState
sealed trait TaxCalculationUnderpaidState extends TaxCalculationState

case class TaxCalculationOverpaidRefundState(amount: BigDecimal, startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationOverpaidState
case class TaxCalculationOverpaidPaymentProcessingState(amount: BigDecimal) extends TaxCalculationOverpaidState
case class TaxCalculationOverpaidPaymentPaidState(amount: BigDecimal, datePaid: String) extends TaxCalculationOverpaidState
case class TaxCalculationOverpaidPaymentChequeSentState(amount: BigDecimal, datePaid: String) extends TaxCalculationOverpaidState

case class TaxCalculationUnderpaidPaymentDueState(amount: BigDecimal, startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationUnderpaidState
case class TaxCalculationUnderpaidPartPaidState(amount: BigDecimal, startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationUnderpaidState
case class TaxCalculationUnderpaidPaidAllState(startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationUnderpaidState
case class TaxCalculationUnderpaidPaymentsDownState(startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationUnderpaidState

case object TaxCalculationUnkownState extends TaxCalculationState


object TaxCalculationState {

  def buildFromTaxCalculation(taxCalculation: Option[TaxCalculation]): TaxCalculationState = {

    taxCalculation match {
      case Some(TaxCalculation("Overpaid", amount, taxYear, Some("REFUND"), _)) =>
        TaxCalculationOverpaidRefundState(amount,  taxYear, taxYear + 1)

      case Some(TaxCalculation("Overpaid", amount, _, Some("PAYMENT_PROCESSING"), _)) =>
        TaxCalculationOverpaidPaymentProcessingState(amount)

      case Some(TaxCalculation("Overpaid", amount, _, Some("PAYMENT_PAID"), Some(datePaid))) =>
        TaxCalculationOverpaidPaymentPaidState(amount, datePaid)

      case Some(TaxCalculation("Overpaid", amount, _, Some("CHEQUE_SENT"), Some(datePaid))) =>
        TaxCalculationOverpaidPaymentChequeSentState(amount, datePaid)

      case Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAYMENT_DUE"), _)) =>
        TaxCalculationUnderpaidPaymentDueState(amount, taxYear, taxYear + 1)

      case Some(TaxCalculation("Underpaid", amount, taxYear, Some("PART_PAID"), _)) =>
        TaxCalculationUnderpaidPartPaidState(amount, taxYear, taxYear + 1)

      case Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAID_ALL"), _)) =>
        TaxCalculationUnderpaidPaidAllState(taxYear, taxYear + 1)

      case Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAYMENTS_DOWN"), _)) =>
        TaxCalculationUnderpaidPaymentsDownState(taxYear, taxYear + 1)

      case _ => TaxCalculationUnkownState
    }
  }
}
