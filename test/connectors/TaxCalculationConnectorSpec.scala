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

package connectors

import models._
import play.api.Application
import play.api.libs.json.Json
import play.api.test.Injecting
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.util.Random

class TaxCalculationConnectorSpec extends ConnectorSpec with WireMockHelper with Injecting {

  override implicit lazy val app: Application = app(
    Map("microservice.services.taxcalc.port" -> server.port())
  )

  lazy val taxCalculationConnector = inject[TaxCalculationConnector]

  val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)
  val url        = s"/taxcalc/$nino/reconciliations"

  val expectedTaxYearListBalanced =
    List(TaxYearReconciliation(2022, Balanced))

  val expectedTaxYearListBalancedResponse: String =
    Json
      .arr(
        Json.obj(
          "taxYear"        -> 2022,
          "reconciliation" -> Json.obj(
            "_type" -> "balanced"
          )
        )
      )
      .toString

  val expectedTaxYearListOverpaid =
    List(TaxYearReconciliation(2022, Reconciliation.overpaid(Some(100), OverpaidStatus.Refund)))

  val expectedTaxYearListOverpaidResponse: String =
    Json
      .arr(
        Json.obj(
          "taxYear"        -> 2022,
          "reconciliation" -> Json.obj(
            "_type"  -> "overpaid",
            "status" -> "refund",
            "amount" -> 100
          )
        )
      )
      .toString

  val expectedTaxYearListUnderpaid =
    List(TaxYearReconciliation(2022, Reconciliation.underpaid(Some(100), None, UnderpaidStatus.PaymentDue)))

  val expectedTaxYearListUnderpaidResponse: String =
    Json
      .arr(
        Json.obj(
          "taxYear"        -> 2022,
          "reconciliation" -> Json.obj(
            "_type"  -> "underpaid",
            "status" -> "payment_due",
            "amount" -> 100
          )
        )
      )
      .toString

  val expectedTaxYearListEmpty = List.empty[TaxYearReconciliation]

  "TaxCalculationConnector" when {
    "getTaxYearReconciliations is called" must {
      List(
        Tuple3(expectedTaxYearListBalancedResponse, expectedTaxYearListBalanced, "Balanced"),
        Tuple3(expectedTaxYearListUnderpaidResponse, expectedTaxYearListUnderpaid, "Underpaid"),
        Tuple3(expectedTaxYearListOverpaidResponse, expectedTaxYearListOverpaid, "Overpaid")
      ).foreach { jsonAndModel =>
        s"return a List[TaxYearReconciliation] containing ${jsonAndModel._3} following an OK response with balanced status" in {
          stubGet(url, OK, Some(jsonAndModel._1))

          val result =
            taxCalculationConnector
              .getTaxYearReconciliations(nino)
              .value
              .futureValue
              .right
              .get // OrElse(expectedTaxYearListEmpty)
          result mustBe jsonAndModel._2
        }
      }
      List(
        BAD_REQUEST,
        NOT_FOUND,
        REQUEST_TIMEOUT,
        UNPROCESSABLE_ENTITY,
        INTERNAL_SERVER_ERROR,
        BAD_GATEWAY,
        SERVICE_UNAVAILABLE
      ).foreach { statusCode =>
        s"return an UpstreamErrorResponse containing $statusCode if the same response is retrieved" in {
          stubGet(url, statusCode, None)

          val result =
            taxCalculationConnector
              .getTaxYearReconciliations(nino)
              .value
              .futureValue
              .swap
              .getOrElse(UpstreamErrorResponse("", OK))
          result.statusCode mustBe statusCode
        }
      }
    }
  }
}
