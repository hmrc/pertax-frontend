/*
 * Copyright 2022 HM Revenue & Customs
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
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import util.DateTimeTools._

import java.time.LocalDate

trait SaDeadlineStatus
object SaDeadlineApproachingStatus extends SaDeadlineStatus
object SaDeadlinePassedStatus extends SaDeadlineStatus

sealed trait TaxCalculationState
sealed trait TaxCalculationOverpaidState extends TaxCalculationState
sealed trait TaxCalculationUnderpaidState extends TaxCalculationState

case class TaxCalculationOverpaidRefundState(amount: BigDecimal, startOfTaxYear: Int, endOfTaxYear: Int)
    extends TaxCalculationOverpaidState
case class TaxCalculationOverpaidPaymentProcessingState(amount: BigDecimal) extends TaxCalculationOverpaidState
case class TaxCalculationOverpaidPaymentPaidState(amount: BigDecimal, datePaid: Option[LocalDate])
    extends TaxCalculationOverpaidState
case class TaxCalculationOverpaidPaymentChequeSentState(amount: BigDecimal, datePaid: Option[LocalDate])
    extends TaxCalculationOverpaidState

case class TaxCalculationUnderpaidPaymentDueState(
  amount: BigDecimal,
  startOfTaxYear: Int,
  endOfTaxYear: Int,
  dueDate: Option[LocalDate],
  saDeadLineStatus: Option[SaDeadlineStatus]
) extends TaxCalculationUnderpaidState
    with SaDeadlineStatus
case class TaxCalculationUnderpaidPartPaidState(
  amount: BigDecimal,
  startOfTaxYear: Int,
  endOfTaxYear: Int,
  dueDate: Option[LocalDate],
  saDeadlineStatus: Option[SaDeadlineStatus]
) extends TaxCalculationUnderpaidState
    with SaDeadlineStatus
case class TaxCalculationUnderpaidPaidAllState(startOfTaxYear: Int, endOfTaxYear: Int, dueDate: Option[LocalDate])
    extends TaxCalculationUnderpaidState
case class TaxCalculationUnderpaidPaymentsDownState(startOfTaxYear: Int, endOfTaxYear: Int)
    extends TaxCalculationUnderpaidState

case object TaxCalculationUnkownState extends TaxCalculationState

@Singleton
class TaxCalculationStateFactory @Inject() (implicit
  val configDecorator: ConfigDecorator,
  val localTaxYearResolver: LocalTaxYearResolver
) {

  def buildFromTaxCalculation(
    taxCalculation: Option[TaxCalculation],
    includeOverPaidPayments: Boolean = true
  ): TaxCalculationState =
    (taxCalculation, includeOverPaidPayments) match {
      case (Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAYMENT_DUE"), _, _, None)), _) =>
        TaxCalculationUnderpaidPaymentDueState(amount, taxYear, taxYear + 1, None, None)

      case (Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAYMENT_DUE"), _, _, Some(dueDate))), _) =>
        TaxCalculationUnderpaidPaymentDueState(
          amount,
          taxYear,
          taxYear + 1,
          Some(LocalDate.parse(dueDate)),
          SaDeadlineStatusCalculator.getSaDeadlineStatus(LocalDate.parse(dueDate))
        )

      case (Some(TaxCalculation("Underpaid", amount, taxYear, Some("PART_PAID"), _, _, None)), _) =>
        TaxCalculationUnderpaidPartPaidState(amount, taxYear, taxYear + 1, None, None)

      case (Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAID_PART"), _, Some("P302"), Some(dueDate))), _) =>
        TaxCalculationUnderpaidPartPaidState(
          amount,
          taxYear,
          taxYear + 1,
          Some(LocalDate.parse(dueDate)),
          SaDeadlineStatusCalculator.getSaDeadlineStatus(LocalDate.parse(dueDate))
        )

      case (Some(TaxCalculation("Underpaid", amount, taxYear, Some("PAID_PART"), _, Some("P302"), _)), _) =>
        TaxCalculationUnderpaidPartPaidState(amount, taxYear, taxYear + 1, None, None)

      case (Some(TaxCalculation("Underpaid", _, taxYear, Some("PAID_ALL"), _, _, None)), _) =>
        TaxCalculationUnderpaidPaidAllState(taxYear, taxYear + 1, None)

      case (Some(TaxCalculation("Underpaid", _, taxYear, Some("PAID_ALL"), _, _, Some(dueDate))), _) =>
        TaxCalculationUnderpaidPaidAllState(taxYear, taxYear + 1, Some(LocalDate.parse(dueDate)))

      case (Some(TaxCalculation("Underpaid", _, taxYear, Some("PAYMENTS_DOWN"), _, _, _)), _) =>
        TaxCalculationUnderpaidPaymentsDownState(taxYear, taxYear + 1)

      case (Some(TaxCalculation("Overpaid", amount, taxYear, Some("REFUND"), _, _, _)), _) =>
        TaxCalculationOverpaidRefundState(amount, taxYear, taxYear + 1)

      case (Some(TaxCalculation("Overpaid", amount, _, Some("PAYMENT_PROCESSING"), _, _, _)), true) =>
        TaxCalculationOverpaidPaymentProcessingState(amount)

      case (Some(TaxCalculation("Overpaid", amount, _, Some("PAYMENT_PAID"), Some(datePaid), _, _)), true) =>
        TaxCalculationOverpaidPaymentPaidState(amount, Some(LocalDate.parse(datePaid)))

      case (Some(TaxCalculation("Overpaid", amount, _, Some("PAYMENT_PAID"), _, _, _)), true) =>
        TaxCalculationOverpaidPaymentPaidState(amount, None)

      case (Some(TaxCalculation("Overpaid", amount, _, Some("CHEQUE_SENT"), Some(datePaid), _, _)), true) =>
        TaxCalculationOverpaidPaymentChequeSentState(amount, Some(LocalDate.parse(datePaid)))

      case _ => TaxCalculationUnkownState
    }
}

object SaDeadlineStatusCalculator {

  def getSaDeadlineStatus(dueDate: LocalDate)(implicit configDecorator: ConfigDecorator): Option[SaDeadlineStatus] = {

    val now                       = configDecorator.currentLocalDate
    val dueDateEquals31stJanuary  = dueDate.getMonthValue == 1 && dueDate.getDayOfMonth == 31
    val dueDatePassed             = now.isAfter(dueDate)
    val datePassed14thDec         =
      now.isAfter(LocalDate.of(taxYearFor(configDecorator.currentLocalDate).currentYear, 12, 14))
    val dateWithin30DaysOfDueDate = now.isAfter(dueDate.minusDays(31))

    (dueDateEquals31stJanuary, dueDatePassed, datePassed14thDec, dateWithin30DaysOfDueDate) match {
      case (true, false, true, _) => Some(SaDeadlineApproachingStatus)
      case (_, false, _, true)    => Some(SaDeadlineApproachingStatus)
      case (_, true, _, _)        => Some(SaDeadlinePassedStatus)
      case _                      => None
    }
  }
}
