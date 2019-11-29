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

package services

import com.google.inject.Inject
import connectors.PayApiConnector
import models.PaymentSearchResult
import org.joda.time.LocalDate
import uk.gov.hmrc.http.HeaderCarrier
import util.DateTimeTools.toPaymentDate
import viewmodels.SelfAssessmentPayment

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentPaymentsService @Inject()(payApiConnector: PayApiConnector) {

  def getPayments(
    utr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[SelfAssessmentPayment]] = {
    implicit val localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isAfter _)
    payApiConnector.findPayments(utr).map(filterAndSortPayments(_))
  }

  def filterAndSortPayments(payments: Option[PaymentSearchResult])(
    implicit order: Ordering[LocalDate]): List[SelfAssessmentPayment] = {

    val successful = "Successful"

    val selfAssessmentPayments =
      payments.fold(List[SelfAssessmentPayment]()) { res =>
        res.payments.filter(payment => payment.status == successful).map { p =>
          SelfAssessmentPayment(
            toPaymentDate(p.createdOn),
            p.reference,
            p.amountInPence.toDouble / 100.00
          )
        }
      }

    if (selfAssessmentPayments.nonEmpty) {
      selfAssessmentPayments.filter(_.date isAfter LocalDate.now.minusDays(61)).sortBy(_.date)
    } else selfAssessmentPayments
  }
}
