/*
 * Copyright 2021 HM Revenue & Customs
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

import models.OverpaidStatus.{Unknown => OverpaidUnknown, _}
import models.UnderpaidStatus.{Unknown => UnderpaidUnknown, _}
import org.joda.time.LocalDate
import play.api.libs.json.{JsError, JsString, Json}
import util.BaseSpec

class TaxYearReconciliationSpec extends BaseSpec {

  "TaxYearReconciliations" must {

    val testList = List(
      ("balanced", Balanced),
      ("underpaid_tolerance", UnderpaidTolerance),
      ("overpaid_tolerance", OverpaidTolerance),
      ("balanced_sa", BalancedSa),
      ("balanced_no_employment", BalancedNoEmployment),
      ("not_reconciled", NotReconciled),
      ("missing", Missing)
    )

    testList.foreach {
      case (name, recType) =>
        s"deserialise a $name type" in {

          val rec = Json.parse(s"""{"_type": "$name"}""")

          rec.as[Reconciliation] mustBe recType
        }
    }

    "deserialise an overpaid type" in {

      val rec = Json.parse("""{"_type": "overpaid", "amount": 100, "status": "refund"}""")

      rec.as[Reconciliation] mustBe Overpaid(Some(100), Refund)
    }

    "deserialise an underpaid type" in {

      val rec =
        Json.parse("""{"_type": "underpaid", "amount": 100, "dueDate": "2019-10-10", "status": "payment_due"}""")

      rec.as[Reconciliation] mustBe Underpaid(Some(100), Some(new LocalDate(2019, 10, 10)), PaymentDue)
    }

    "fail to deserialise another value" in {

      val rec = Json.parse("""{"_type": "abc123", "amount": 100, "dueDate": "2019-10-10", "status": "payment_due"}""")

      rec.validate[Reconciliation] mustBe JsError("Could not parse Reconciliation")
    }

    val overpaidList = List(
      ("refund", Refund),
      ("payment_processing", PaymentProcessing),
      ("payment_paid", PaymentPaid),
      ("cheque_sent", ChequeSent),
      ("sa_user", SaUser),
      ("unable_to_claim", UnableToClaim),
      ("unknown", OverpaidUnknown)
    )

    overpaidList.foreach {
      case (name, overpaidType) =>
        s"deserialise an Overpaid $name type" in {

          val rec = JsString(name)

          rec.as[OverpaidStatus] mustBe overpaidType
        }
    }

    "fail to deserialise another Overpaid value" in {

      val rec = JsString("abc123")

      rec.validate[OverpaidStatus] mustBe JsError("Could not parse Overpaid status")
    }

    val underpaidList = List(
      ("payment_due", PaymentDue),
      ("part_paid", PartPaid),
      ("paid_all", PaidAll),
      ("payments_down", PaymentsDown),
      ("unknown", UnderpaidUnknown)
    )

    underpaidList.foreach {
      case (name, underpaidType) =>
        s"deserialise an Underpaid $name type" in {

          val rec = JsString(name)

          rec.as[UnderpaidStatus] mustBe underpaidType
        }
    }

    "fail to deserialise another Underpaid value" in {

      val rec = JsString("abc123")

      rec.validate[UnderpaidStatus] mustBe JsError("Could not parse Underpaid status")
    }
  }
}
