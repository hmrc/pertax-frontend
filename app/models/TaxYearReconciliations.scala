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

import play.api.libs.json._

import scala.math.BigDecimal

case class TaxYearReconciliations(nino: String, taxYears: List[TaxYearReconciliation])

object TaxYearReconciliations {

  implicit val httpReads: Reads[TaxYearReconciliations] = Json.reads[TaxYearReconciliations]
}

case class TaxYearReconciliation(year: Int, reconciliation: List[Reconciliation])

object TaxYearReconciliation {

  implicit val httpReads: Reads[TaxYearReconciliation] = Json.reads[TaxYearReconciliation]
}

case class Reconciliation(
                         reconciliationStatus: Option[ReconciliationStatus],
                         cumulativeAmount: Double,
                         p800Status: Option[P800Status]
                         )

object Reconciliation {

  implicit val httpReads: Reads[Reconciliation] = Json.reads[Reconciliation]
}

sealed trait ReconciliationStatus

object ReconciliationStatus {

  case object Balanced extends ReconciliationStatus
  case object OpTolerance extends ReconciliationStatus
  case object UpTolerance extends ReconciliationStatus
  case object Overpaid extends ReconciliationStatus
  case object Underpaid extends ReconciliationStatus
  case object BalancedSA extends ReconciliationStatus
  case object BalancedNoEmp extends ReconciliationStatus
  case object NoRec extends ReconciliationStatus

  implicit val reconciliationStatusReads: Reads[ReconciliationStatus] = new Reads[ReconciliationStatus] {
    override def reads(json: JsValue): JsResult[ReconciliationStatus] = json match {

      case JsNumber(x) if x == 1 => JsSuccess(Balanced)
      case JsNumber(x) if x == 2 => JsSuccess(OpTolerance)
      case JsNumber(x) if x == 3 => JsSuccess(UpTolerance)
      case JsNumber(x) if x == 4 => JsSuccess(Overpaid)
      case JsNumber(x) if x == 5 => JsSuccess(Underpaid)
      case JsNumber(x) if x == 7 => JsSuccess(BalancedSA)
      case JsNumber(x) if x == 8 => JsSuccess(BalancedNoEmp)
      case _ => JsSuccess(NoRec)
    }
  }
}

sealed trait P800Status

object P800Status {

  case object Issued extends P800Status
  case object Cancelled extends P800Status
  case object CancelledByUser extends P800Status

  implicit val p800StatusReads: Reads[P800Status] = new Reads[P800Status] {
    override def reads(json: JsValue): JsResult[P800Status] = json match {

      case JsNumber(x) if x == 2 => JsSuccess(Issued)
      case JsNumber(x) if x == 3 => JsSuccess(Cancelled)
      case JsNumber(x) if x == 4 => JsSuccess(CancelledByUser)
      case _ => JsError("Unable to parse P800 status")
    }
  }
}

