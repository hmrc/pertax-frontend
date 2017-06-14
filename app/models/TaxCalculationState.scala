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
case class TaxCalculationRefundState(amount: BigDecimal, startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationState
case class TaxCalculationPaymentProcessingState(amount: BigDecimal) extends TaxCalculationState
case class TaxCalculationPaymentPaidState(amount: BigDecimal, datePaid: String) extends TaxCalculationState
case class TaxCalculationPaymentChequeSentState(amount: BigDecimal, datePaid: String) extends TaxCalculationState
case class TaxCalculationPaymentDueState(amount: BigDecimal, startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationState
case class TaxCalculationPartPaidState(amount: BigDecimal, startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationState
case class TaxCalculationPaidAllState() extends TaxCalculationState
case class TaxCalculationPaymentsDownState(startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationState
case object TaxCalculationNotFoundState extends TaxCalculationState
case class TaxCalculationUnderpaymentState(amount: BigDecimal, startOfTaxYear: Int, endOfTaxYear: Int) extends TaxCalculationState

object TaxCalculationState {

  def buildFromTaxCalculation(taxCalculation: Option[TaxCalculation]): TaxCalculationState = {

    taxCalculation match {
      case Some(TaxCalculation("Overpaid", amount, taxYear, Some("REFUND"), _)) =>
        TaxCalculationRefundState(amount,  taxYear, taxYear + 1)
      case Some(TaxCalculation("Overpaid", amount, _, Some("PAYMENT_PROCESSING"), _)) =>
        TaxCalculationPaymentProcessingState(amount)
      case Some(TaxCalculation("Overpaid", amount, _, Some("PAYMENT_PAID"), Some(datePaid))) =>
        TaxCalculationPaymentPaidState(amount, datePaid)
      case Some(TaxCalculation("Overpaid", amount, _, Some("CHEQUE_SENT"), Some(datePaid))) =>
        TaxCalculationPaymentChequeSentState(amount, datePaid)
      case Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAYMENT_DUE"), _)) =>
        TaxCalculationPaymentDueState(amount, taxYear, taxYear + 1)
      case Some(TaxCalculation("Underpaid", amount, taxYear, Some("PART_PAID"), _)) =>
        TaxCalculationPartPaidState(amount, taxYear, taxYear + 1)
      case Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAID_ALL"), _)) =>
        TaxCalculationPaidAllState()
      case Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAYMENTS_DOWN"), _)) =>
        TaxCalculationPaymentsDownState(taxYear, taxYear + 1)
      case _ => TaxCalculationNotFoundState
    }
  }
}
