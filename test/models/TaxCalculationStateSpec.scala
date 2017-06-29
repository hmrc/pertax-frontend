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

import util.BaseSpec

class TaxCalculationStateSpec extends BaseSpec {

  "Calling buildFromTaxCalcSummary " should {

    "return a TaxCalcRefundState when called with a TaxCalculation with a P800 status of Overpaid and paymentStatus of REFUND" in {
      val taxCalculation = TaxCalculation("Overpaid", 1000.0, 2015, Some("REFUND"), None)
      val result = TaxCalculationState.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationRefundState(1000.0, 2015, 2016)
    }

    "return a TaxCalculationPaymentProcessingState when called with a TaxCalculation with a P800 status of Overpaid and paymentStatus of PAYMENT_PROCESSING" in {
      val taxCalculation = TaxCalculation("Overpaid", 1000.0, 2015, Some("PAYMENT_PROCESSING"), None)
      val result = TaxCalculationState.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationPaymentProcessingState(1000.0)
    }

    "return a TaxCalculationPaymentPaidState when called with a TaxCalculation with a P800 status of Overpaid and paymentStatus of PAYMENT_PAID" in {
      val taxCalculation = TaxCalculation("Overpaid", 1000.0, 2015, Some("PAYMENT_PAID"), Some("19 May 2016"))
      val result = TaxCalculationState.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationPaymentPaidState(1000.0, "19 May 2016")
    }

    "return a TaxCalculationPaymentChequeSentState when called with a TaxCalculation with a P800 status of Overpaid and paymentStatus of CHEQUE_SENT" in {
      val taxCalculation = TaxCalculation("Overpaid", 1000.0, 2015, Some("CHEQUE_SENT"), Some("19 May 2016"))
      val result = TaxCalculationState.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationPaymentChequeSentState(1000.0, "19 May 2016")
    }

    "return a TaxCalculation not found when called without a TaxCalculation" in {
      val result = TaxCalculationState.buildFromTaxCalculation(None)
      result shouldBe TaxCalculationUnkownState
    }

    "return a TaxCalculationPaymentDueState when called with a TaxCalculation with a P800 status of PAYMENT_DUE" in {
      val taxCalculation = TaxCalculation("Underpaid", 1000.0, 2015, Some("PAYMENT_DUE"), None)
      val result = TaxCalculationState.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationPaymentDueState(1000.0, 2015, 2016)
    }

    "return a TaxCalculationPartPaidState when called with a TaxCalculation with a P800 status of PART_PAID" in {
      val taxCalculation = TaxCalculation("Underpaid", 1000.0, 2015, Some("PART_PAID"), None)
      val result = TaxCalculationState.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationPartPaidState(1000.0, 2015, 2016)
    }

    "return a TaxCalculationPaidAllState when called with a TaxCalculation with a P800 status of PAID_ALL" in {
      val taxCalculation = TaxCalculation("Underpaid", 1000.0, 2015, Some("PAID_ALL"), None)
      val result = TaxCalculationState.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationPaidAllState
    }

    "return a TaxCalculationPaymentsDownState when called with a TaxCalculation with a P800 status of PAYMENTS_DOWN" in {
      val taxCalculation = TaxCalculation("Underpaid", 1000.0, 2015, Some("PAYMENTS_DOWN"), None)
      val result = TaxCalculationState.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationPaymentsDownState(2015, 2016)
    }

  }

}
