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
import org.joda.time.LocalDate
import play.api.libs.json._
import models.SaDeadlineStatusCalculator._

case class TaxYearReconciliation(taxYear: Int, reconciliation: Reconciliation)

object TaxYearReconciliation {

  implicit val httpReads: Reads[TaxYearReconciliation] = Json.reads[TaxYearReconciliation]
}

sealed trait Reconciliation

object Reconciliation {

  implicit val reads: Reads[Reconciliation] = new Reads[Reconciliation] {
    override def reads(json: JsValue): JsResult[Reconciliation] = json \ "_type" match {

      case JsDefined(JsString("balanced"))               => JsSuccess(Balanced)
      case JsDefined(JsString("underpaid_tolerance"))    => JsSuccess(UnderpaidTolerance)
      case JsDefined(JsString("overpaid_tolerance"))     => JsSuccess(OverpaidTolerance)
      case JsDefined(JsString("underpaid"))              => json.validate[Underpaid]
      case JsDefined(JsString("overpaid"))               => json.validate[Overpaid]
      case JsDefined(JsString("balanced_sa"))            => JsSuccess(BalancedSa)
      case JsDefined(JsString("balanced_no_employment")) => JsSuccess(BalancedNoEmployment)
      case JsDefined(JsString("not_reconciled"))         => JsSuccess(NotReconciled)
      case _                                             => JsError("Could not parse Reconciliation")
    }
  }

  def underpaid(amount: Option[Double], dueDate: Option[LocalDate], status: UnderpaidStatus): Reconciliation =
    Underpaid(amount, dueDate, status)

  def overpaid(amount: Option[Double], status: OverpaidStatus): Reconciliation = Overpaid(amount, status)

  def notReconciled: Reconciliation = NotReconciled
}

case class Underpaid(amount: Option[Double], dueDate: Option[LocalDate], status: UnderpaidStatus)
    extends Reconciliation {

  def isDeadlineApproaching(implicit cd: ConfigDecorator): Boolean = dueDate.exists { x =>
    getSaDeadlineStatus(x).contains(SaDeadlineApproachingStatus)
  }

  def hasDeadlinePassed(implicit cd: ConfigDecorator): Boolean = dueDate.exists { x =>
    getSaDeadlineStatus(x).contains(SaDeadlinePassedStatus)
  }
}

object Underpaid {

  implicit val reads: Reads[Underpaid] = Json.reads[Underpaid]
}

case class Overpaid(amount: Option[Double], status: OverpaidStatus) extends Reconciliation

object Overpaid {

  implicit val reads: Reads[Overpaid] = Json.reads[Overpaid]
}

case object Balanced extends Reconciliation

case object OverpaidTolerance extends Reconciliation

case object UnderpaidTolerance extends Reconciliation

case object BalancedSa extends Reconciliation

case object BalancedNoEmployment extends Reconciliation

case object NotReconciled extends Reconciliation

case object Missing extends Reconciliation

sealed trait UnderpaidStatus

object UnderpaidStatus {

  implicit val reads: Reads[UnderpaidStatus] = new Reads[UnderpaidStatus] {
    override def reads(json: JsValue): JsResult[UnderpaidStatus] = json match {
      case JsString("payment_due")   => JsSuccess(PaymentDue)
      case JsString("part_paid")     => JsSuccess(PartPaid)
      case JsString("paid_all")      => JsSuccess(PaidAll)
      case JsString("payments_down") => JsSuccess(PaymentsDown)
      case JsString("unknown")       => JsSuccess(Unknown)
      case _                         => JsError("Could not parse Underpaid status")
    }
  }
  case object PaymentDue extends UnderpaidStatus

  case object PartPaid extends UnderpaidStatus

  case object PaidAll extends UnderpaidStatus

  case object PaymentsDown extends UnderpaidStatus

  case object Unknown extends UnderpaidStatus

}

sealed trait OverpaidStatus

object OverpaidStatus {

  implicit val reads: Reads[OverpaidStatus] = new Reads[OverpaidStatus] {
    override def reads(json: JsValue): JsResult[OverpaidStatus] = json match {
      case JsString("refund")             => JsSuccess(Refund)
      case JsString("payment_processing") => JsSuccess(PaymentProcessing)
      case JsString("payment_paid")       => JsSuccess(PaymentPaid)
      case JsString("cheque_sent")        => JsSuccess(ChequeSent)
      case JsString("sa_user")            => JsSuccess(SaUser)
      case JsString("unable_to_claim")    => JsSuccess(UnableToClaim)
      case JsString("unknown")            => JsSuccess(Unknown)
      case _                              => JsError("Could not parse Overpaid status")
    }
  }

  case object Refund extends OverpaidStatus

  case object PaymentProcessing extends OverpaidStatus

  case object PaymentPaid extends OverpaidStatus

  case object ChequeSent extends OverpaidStatus

  case object SaUser extends OverpaidStatus

  case object UnableToClaim extends OverpaidStatus

  case object Unknown extends OverpaidStatus

}
