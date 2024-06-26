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

package models

import play.api.libs.json._

sealed trait ReconciliationStatus

case object NoReconciliationStatus extends ReconciliationStatus
case object Balanced extends ReconciliationStatus
case object OverpaidWithinTolerance extends ReconciliationStatus
case object UnderpaidWithinTolerance extends ReconciliationStatus
case object Overpaid extends ReconciliationStatus
case object Underpaid extends ReconciliationStatus
case object BalancedSA extends ReconciliationStatus
case object BalancedNoEmployment extends ReconciliationStatus

object ReconciliationStatus {
  private val statusValues: Map[Int, ReconciliationStatus] = Map(
    -1 -> NoReconciliationStatus,
    1  -> Balanced,
    2  -> OverpaidWithinTolerance,
    3  -> UnderpaidWithinTolerance,
    4  -> Overpaid,
    5  -> Underpaid,
    7  -> BalancedSA,
    8  -> BalancedNoEmployment
  )

  implicit def reads: Reads[ReconciliationStatus] = (__ \ "code").read[Int].map(statusValues)
}
