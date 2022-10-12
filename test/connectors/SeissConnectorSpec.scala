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

import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, SeissModel}
import play.api.Application
import play.api.libs.json.JsResultException
import play.api.mvc.AnyContentAsEmpty
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.util.UUID

class SeissConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout {

  val url                = "/self-employed-income-support/get-claims"
  val utr: SaUtr         = new SaUtrGenerator().nextSaUtr
  val providerId: String = UUID.randomUUID().toString

  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = userRequest(
    ActivatedOnlineFilerSelfAssessmentUser(utr),
    providerId
  )

  override implicit lazy val app: Application = app(
    Map("microservice.services.self-employed-income-support.port" -> server.port())
  )

  def connector: SeissConnector = app.injector.instanceOf[SeissConnector]

  "SeissConnector" when {
    val requestBody: String =
      s"""
         |{
         |   "utr": "$utr"
         |}
      """.stripMargin

    "getClaims is called" must {
      "return list of claims" when {
        "the user has seiss data" in {
          val response =
            s"""
               |[
               |    {
               |        "_id": 1135371,
               |        "utr": "1234567890",
               |        "paymentReference": "SESE1135371",
               |        "barsRequestId": 1014,
               |        "claimant": {
               |            "name": "Foo Bar",
               |            "phoneNumber": "0772344600",
               |            "email": "foo1@example.com",
               |            "address": {
               |                "line1": "2 Other Place",
               |                "line2": "Some District",
               |                "town": "Anytown",
               |                "postCode": "ZZ11ZZ"
               |            }
               |        },
               |        "bankDetails": {
               |            "accountName": "Alex Askew",
               |            "sortCode": "206705",
               |            "accountNumber": "44344611"
               |        },
               |        "claimAmount": 45000,
               |        "status": "pendingBarsCheck",
               |        "riskingResponse": {
               |            "action": "ACCEPT"
               |        },
               |        "applicationSubmissionDate": "2021-10-26T15:07:40.439",
               |        "claimPhase": 5,
               |        "paymentEvents": [],
               |        "financialImpactInfo": {
               |            "subjectTurnover": 1200,
               |            "comparisonTurnover": 1200,
               |            "comparisonYear": 2020,
               |            "multiplier": 0.3
               |        }
               |    }
               |]
            """.stripMargin

          stubPost(url, OK, Some(requestBody), Some(response))

          val result = connector.getClaims(utr.toString()).value.futureValue
          result mustBe a[Right[_, _]]
          result.right.get mustBe List(SeissModel("1234567890"))
        }
      }

      "return empty list" when {
        "the user has no seiss data" in {
          val response =
            s"""
               |[
               |]
            """.stripMargin

          stubPost(url, OK, Some(requestBody), Some(response))

          val result = connector.getClaims(utr.toString()).value.futureValue
          result mustBe a[Right[_, _]]
          result.right.get mustBe empty
        }
      }

      "return an UpstreamErrorResponse" when {
        List(BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND).foreach(statusCode =>
          s"a status $statusCode is returned" in {
            stubPost(url, statusCode, Some(requestBody), None)

            val result = connector.getClaims(utr.toString()).value.futureValue
            result mustBe a[Left[_, _]]
            result.left mustBe UpstreamErrorResponse(_: String, statusCode)
          }
        )

        "there is a timeout" in {
          val delay: Int = 5000
          stubWithDelay(url, OK, Some(requestBody), None, delay)

          val result = connector.getClaims(utr.toString()).value.futureValue
          result mustBe a[Left[_, _]]
          result.left.get mustBe UpstreamErrorResponse(_: String, INTERNAL_SERVER_ERROR)
        }
      }

      "return an exception" when {
        "the response body is not valid" in {
          stubPost(url, OK, Some(requestBody), Some("""{"invalid":"invalid"}"""))

          lazy val result = await(connector.getClaims(utr.toString()).value)
          a[JsResultException] mustBe thrownBy(result)
        }
      }
    }
  }

}
