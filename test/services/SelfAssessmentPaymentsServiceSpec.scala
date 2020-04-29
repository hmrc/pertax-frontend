/*
 * Copyright 2020 HM Revenue & Customs
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

package services

import java.time.LocalDateTime

import connectors.PayApiConnector
import models.{PayApiPayment, PaymentSearchResult}
import org.joda.time.LocalDate
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.http.Status._
import uk.gov.hmrc.http.Upstream5xxResponse
import util.BaseSpec
import viewmodels.SelfAssessmentPayment

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SelfAssessmentPaymentsServiceSpec extends BaseSpec with MockitoSugar with ScalaFutures {

  val mockPayApiConnector = mock[PayApiConnector]

  def sut = new SelfAssessmentPaymentsService(mockPayApiConnector)

  implicit val localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isAfter _)

  "Calling SelfAssessmentController.getPayments" should {

    "return an empty list if no payments were retrieved from connector" in {

      when(mockPayApiConnector.findPayments(any())(any(), any())) thenReturn Future.successful(None)

      sut.getPayments("111111111").futureValue shouldBe List.empty
    }

    "return a list of payments if payments are retrieved from connector" in {

      val payments = List(
        PayApiPayment("Successful", Some(2000), "111111111K", LocalDateTime.now().minusDays(34.toLong)),
        PayApiPayment("Successful", Some(3000), "111111111K", LocalDateTime.now().minusDays(47.toLong)),
        PayApiPayment("Successful", Some(4000), "111111111K", LocalDateTime.now().minusDays(59.toLong))
      )

      val response = Some(
        PaymentSearchResult(
          "test",
          "test",
          payments
        ))

      val expectedResult = List(
        SelfAssessmentPayment(LocalDate.now().minusDays(34), "111111111K", 20.0),
        SelfAssessmentPayment(LocalDate.now().minusDays(47), "111111111K", 30.0),
        SelfAssessmentPayment(LocalDate.now().minusDays(59), "111111111K", 40.0)
      )

      when(mockPayApiConnector.findPayments(any())(any(), any())) thenReturn Future.successful(response)

      sut.getPayments("111111111").futureValue shouldBe expectedResult
    }

    "return an Upstream5xxResponse if an Upstream5xxResponse is thrown by the connector" in {

      val connectorResponse = Upstream5xxResponse("failed", BAD_GATEWAY, INTERNAL_SERVER_ERROR)

      when(mockPayApiConnector.findPayments(any())(any(), any())) thenReturn
        Future.failed(connectorResponse)

      sut.getPayments("111111111").failed.futureValue shouldBe connectorResponse
    }

  }

  "Calling SelfAssessmentController.filterAndSortPayments" should {

    "return an empty list if no payments are present" in {

      val list = Some(PaymentSearchResult("PTA", "111111111", List.empty))

      val result = sut.filterAndSortPayments(list)

      result.isEmpty shouldBe true
    }

    "filter payments to only include payments in the past 60 days" in {

      val outlier = SelfAssessmentPayment(LocalDate.now().minusDays(61), "KT123459", 7.00)

      val payments = List(
        PayApiPayment("Successful", Some(14587), "111111111K", LocalDateTime.now().minusDays(11.toLong)),
        PayApiPayment("Successful", Some(6354), "111111111K", LocalDateTime.now().minusDays(27.toLong)),
        PayApiPayment("Successful", Some(700), "111111111K", LocalDateTime.now().minusDays(61.toLong)),
        PayApiPayment("Successful", Some(1231), "111111111K", LocalDateTime.now().minusDays(60.toLong))
      )

      val list = Some(PaymentSearchResult("PTA", "111111111", payments))

      val result = sut.filterAndSortPayments(list)

      result should not contain (outlier)
      result.length shouldBe 3

    }

    "filter payments to only include Successful payments" in {

      val payments = List(
        PayApiPayment("Successful", Some(25601), "111111111K", LocalDateTime.now()),
        PayApiPayment("Successful", Some(1300), "111111111K", LocalDateTime.now().minusDays(12.toLong)),
        PayApiPayment("Cancelled", Some(14021), "111111111K", LocalDateTime.now().minusDays(47.toLong)),
        PayApiPayment("Failed", Some(17030), "111111111K", LocalDateTime.now().minusDays(59.toLong))
      )

      val list = Some(PaymentSearchResult("PTA", "111111111", payments))

      val cancelled = SelfAssessmentPayment(LocalDate.now().minusDays(47), "111111111K", 140.21)

      val failed = SelfAssessmentPayment(LocalDate.now().minusDays(59), "111111111K", 170.30)

      val result = sut.filterAndSortPayments(list)

      result should contain noneOf (cancelled, failed)
      result.length shouldBe 2
    }

    "order payments from latest payment descending" in {

      val apiPayments = List(
        PayApiPayment("Successful", Some(25601), "111111111K", LocalDateTime.now()),
        PayApiPayment("Successful", Some(1300), "111111111K", LocalDateTime.now().minusDays(12.toLong)),
        PayApiPayment("Successful", Some(17030), "111111111K", LocalDateTime.now().minusDays(59.toLong))
      )

      val list = Some(PaymentSearchResult("PTA", "111111111", apiPayments))

      val selfAssessmentPayments = List(
        SelfAssessmentPayment(LocalDate.now(), "111111111K", 256.01),
        SelfAssessmentPayment(LocalDate.now().minusDays(12), "111111111K", 13.0),
        SelfAssessmentPayment(LocalDate.now().minusDays(59), "111111111K", 170.3)
      )

      sut.filterAndSortPayments(list) shouldBe selfAssessmentPayments

    }
  }
}
