/*
 * Copyright 2018 HM Revenue & Customs
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

import controllers.AddressControllerConfiguration
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import util.BaseSpec
import play.api.inject.bind
import uk.gov.hmrc.time.TaxYearResolver

class TaxCalculationStateSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[AddressControllerConfiguration].toInstance(MockitoSugar.mock[AddressControllerConfiguration]))
    .build()
  
  "Calling buildFromTaxCalcSummary without a P302 business reason" should {

    trait TaxCalculationStateSimpleSpecSetup {
      lazy val controller = injected[TaxCalculationStateFactory]
    }

    "return a TaxCalcRefundState when called with a TaxCalculation with a P800 status of Overpaid and paymentStatus of REFUND" in new TaxCalculationStateSimpleSpecSetup {
      val taxCalculation = TaxCalculation("Overpaid", 1000.0, 2015, Some("REFUND"), None, None, None)
      val result = controller.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationOverpaidRefundState(1000.0, 2015, 2016)
    }

    "return a TaxCalculationPaymentProcessingState when called with a TaxCalculation with a P800 status of Overpaid and paymentStatus of PAYMENT_PROCESSING" in new TaxCalculationStateSimpleSpecSetup {
      val taxCalculation = TaxCalculation("Overpaid", 1000.0, 2015, Some("PAYMENT_PROCESSING"), None, None, None)
      val result = controller.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationOverpaidPaymentProcessingState(1000.0)
    }

    "return a TaxCalculationPaymentPaidState when called with a TaxCalculation with a P800 status of Overpaid and paymentStatus of PAYMENT_PAID" in new TaxCalculationStateSimpleSpecSetup {
      val taxCalculation = TaxCalculation("Overpaid", 1000.0, 2015, Some("PAYMENT_PAID"), Some("19 May 2016"), None, None)
      val result = controller.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationOverpaidPaymentPaidState(1000.0, "19 May 2016")
    }

    "return a TaxCalculationPaymentChequeSentState when called with a TaxCalculation with a P800 status of Overpaid and paymentStatus of CHEQUE_SENT" in new TaxCalculationStateSimpleSpecSetup {
      val taxCalculation = TaxCalculation("Overpaid", 1000.0, 2015, Some("CHEQUE_SENT"), Some("19 May 2016"), None, None)
      val result = controller.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationOverpaidPaymentChequeSentState(1000.0, "19 May 2016")
    }

    "return a TaxCalculation not found when called without a TaxCalculation" in new TaxCalculationStateSimpleSpecSetup {
      val result = controller.buildFromTaxCalculation(None)
      result shouldBe TaxCalculationUnkownState
    }

    "return a TaxCalculationPaymentDueState when called with a TaxCalculation with a P800 status of PAYMENT_DUE" in new TaxCalculationStateSimpleSpecSetup {
      val taxCalculation = TaxCalculation("Underpaid", 1000.0, 2015, Some("PAYMENT_DUE"), None, None, None)
      val result = controller.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2015, 2016, None, None)
    }

    "return a TaxCalculationPartPaidState when called with a TaxCalculation with a P800 status of PART_PAID" in new TaxCalculationStateSimpleSpecSetup {
      val taxCalculation = TaxCalculation("Underpaid", 1000.0, 2015, Some("PART_PAID"), None, None, None)
      val result = controller.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2015, 2016, None, None)
    }

    "return a TaxCalculationPaidAllState when called with a TaxCalculation with a P800 status of PAID_ALL" in new TaxCalculationStateSimpleSpecSetup {
      val taxCalculation = TaxCalculation("Underpaid", 1000.0, 2015, Some("PAID_ALL"), None, None, None)
      val result = controller.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationUnderpaidPaidAllState(2015, 2016, None)
    }

    "return a TaxCalculationPaymentsDownState when called with a TaxCalculation with a P800 status of PAYMENTS_DOWN" in new TaxCalculationStateSimpleSpecSetup {
      val taxCalculation = TaxCalculation("Underpaid", 1000.0, 2015, Some("PAYMENTS_DOWN"), None, None, None)
      val result = controller.buildFromTaxCalculation(Some(taxCalculation))
      result shouldBe TaxCalculationUnderpaidPaymentsDownState(2015, 2016)
    }
  }

  "Calling buildFromTaxCalcSummary with a P302 business reason" should {

    trait TaxCalculationStateCurrentDateSpecSetup {
      def currentDateTime: String
      def paymentStatus: String
      def dueDate: String

      lazy val controller = {
        when(injected[AddressControllerConfiguration].currentDateWithTimeAtStartOfDay) thenReturn DateTime.parse(currentDateTime).withTimeAtStartOfDay
        injected[TaxCalculationStateFactory]
      }

      lazy val taxCalculation = TaxCalculation("Underpaid", 1000.0, 2017, Some(paymentStatus), None, Some("P302"), Some(dueDate))
      lazy val result = controller.buildFromTaxCalculation(Some(taxCalculation))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of None and due date when\n" +
    "    today's date is 14/12 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
    "    due date is 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {
      override lazy val currentDateTime = "2017-12-14"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2018-01-31"), None)
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproaching and due date when\n" +
    "    today's date is 15/12 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
    "    due date is 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-12-15"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproaching and due date when\n" +
    "    today's date is 16/12 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
    "    due date is 31/01/2018\n"in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-12-16"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of None and due date when\n" +
    "    today's date is 14/12 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-12-14"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2018-01-31"), None)
    }

    "return a TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlineApproaching and due date when\n" +
    "    today's date is 15/12 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is to 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-12-15"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlineApproaching and due date when-\n" +
    "    today's date is 16/12 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is to 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-12-16"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of None and due date when\n" +
    "    today's date is 30/10/2017 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
    "    due date is 30/11/2017 (today's date is NOT within due date minus 30 days)\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-10-30"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2017-11-30"), None)
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproaching and due date when-\n" +
    "    today's date is 31/10/2017 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
    "    due date is 30/11/2017 (today's date IS within due date minus 30 days)\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-10-31"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2017-11-30"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproaching and due date when-\n" +
    "    today's date is 01/11/2017 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
    "    due date is 30/11/2017 (today's date IS within due date minus 30 days)\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-11-01"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2017-11-30"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of None and due date when\n" +
    "    today's date is 30/10/2017 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is 30/11/2017 (today's date is NOT within due date minus 30 days)\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-10-30"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2017-11-30"), None)
    }

    "return a TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlineApproaching and due date when-\n" +
    "    today's date is 31/10/2017 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is 30/11/2017 (today's date IS within due date minus 30 days)\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-10-31"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2017-11-30"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlineApproaching and due date when-\n" +
    "    today's date is 01/11/2017 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is 30/11/2017 (today's date IS within due date minus 30 days)\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-11-01"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2017-11-30"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproachingStatus and due date when-\n" +
      "    today's date is 31/01/2018 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
      "    due date is 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2018-01-31"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproachingStatus and due date when-\n" +
      "    today's date is 30/11/2017 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
      "    due date is 30/11/2017\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-11-30"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2017-11-30"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlinePassedStatus and due date when-\n" +
    "    today's date is 01/02/2018 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
    "    due date is 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2018-02-01"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlinePassedStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlinePassedStatus and due date when-\n" +
    "    today's date is 02/02/2018 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
    "    due date is 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2018-02-02"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlinePassedStatus))
    }

    "return a TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlineApproachingStatus and due date when-\n" +
    "    today's date is 31/01/2018 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2018-01-31"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlineApproachingStatus and due date when-\n" +
      "    today's date is 30/11/2017 in the current tax year for P800 status of 'PART_PAID'\n" +
      "    due date is 30/11/2017\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-11-30"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2017-11-30"), Some(SaDeadlineApproachingStatus))
    }

    "return a TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlinePassedStatus and due date when-\n" +
    "    today's date is 01/02/2018 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2018-02-01"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlinePassedStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlinePassedStatus and due date when-\n" +
    "    today's date is 02/02/2018 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is 31/01/2018\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2018-02-02"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2018-01-31"), Some(SaDeadlinePassedStatus))
    }

    "return a TaxCalculationUnderpaidPaymentDueState with SaDeadlineStatus of SaDeadlinePassedStatus and due date when-\n" +
    "    today's date is 01/12/2017 in the current tax year for P800 status of 'PAYMENT_DUE'\n" +
    "    due date is 30/12/2017 (due date passed by one day)\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-12-01"
      override lazy val paymentStatus = "PAYMENT_DUE"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPaymentDueState(1000.0, 2017, 2018, Some("2017-11-30"), Some(SaDeadlinePassedStatus))
    }

    "return a TaxCalculationUnderpaidPartPaidState with SaDeadlineStatus of SaDeadlinePassedStatus and due date when-\n" +
    "    today's date is 01/12/2017 in the current tax year for P800 status of 'PART_PAID'\n" +
    "    due date is 30/12/2017 (due date passed by one day)\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2017-12-01"
      override lazy val paymentStatus = "PART_PAID"
      override lazy val dueDate = "2017-11-30"

      result shouldBe TaxCalculationUnderpaidPartPaidState(1000.0, 2017, 2018, Some("2017-11-30"), Some(SaDeadlinePassedStatus))
    }

    "return a TaxCalculationUnderpaidPaidAllState with due date when-\n" +
    "    today's date is 01/12/2017 in the current tax year for P800 status of 'PAID_ALL'\n" +
    "    due date is 30/12/2017\n" in new TaxCalculationStateCurrentDateSpecSetup {

      override lazy val currentDateTime = "2018-01-31"
      override lazy val paymentStatus = "PAID_ALL"
      override lazy val dueDate = "2018-01-31"

      result shouldBe TaxCalculationUnderpaidPaidAllState(2017, 2018, Some("2018-01-31"))
    }
  }
}
