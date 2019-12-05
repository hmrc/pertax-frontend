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

import java.time.LocalDateTime

import play.api.libs.json.Json
import uk.gov.hmrc.http.{HttpResponse, Upstream5xxResponse}
import util.BaseSpec

class PayApiModelsSpec extends BaseSpec {

  trait LocalSetup {

    val httpResponse: HttpResponse
  }

  "PaymentSearchResult HttpReads" when {

    "Status is OK and Json is valid" should {

      "return a PaymentSearchResult" in new LocalSetup {

        val json = Json.parse("""
                                |{
                                |   "searchScope":"PTA",
                                |   "searchTag":"1097172564",
                                |   "payments": [
                                |      {
                                |         "id":"5ddbd2847a0000c7f0d845a4",
                                |         "reference":"1097172564",
                                |         "amountInPence":10623,
                                |         "status":"Successful",
                                |         "createdOn":"2019-11-25T13:09:24.188",
                                |         "taxType":"self-assessment"
                                |      }
                                |   ]
                                |}
                                |
                                |""".stripMargin)

        override val httpResponse: HttpResponse = HttpResponse(200, Some(json))

        val expected = PaymentSearchResult(
          "PTA",
          "1097172564",
          List(PayApiPayment("Successful", Some(10623), "1097172564", LocalDateTime.parse("2019-11-25T13:09:24.188")))
        )

        PaymentSearchResult.httpReads.read("GET", "testUrl", httpResponse) shouldBe Some(expected)

      }
    }

    "status is OK and Json contains null for amount (unfinished payment)" in new LocalSetup {

      val json = Json.parse("""
                              |{
                              |   "searchScope":"PTA",
                              |   "searchTag":"1097172564",
                              |   "payments": [
                              |      {
                              |         "id":"5ddbd2847a0000c7f0d845a4",
                              |         "reference":"1097172564",
                              |         "amountInPence":null,
                              |         "status":"Successful",
                              |         "createdOn":"2019-11-25T13:09:24.188",
                              |         "taxType":"self-assessment"
                              |      }
                              |   ]
                              |}
                              |
                              |""".stripMargin)

      override val httpResponse: HttpResponse = HttpResponse(200, Some(json))

      val expected = PaymentSearchResult(
        "PTA",
        "1097172564",
        List(PayApiPayment("Successful", None, "1097172564", LocalDateTime.parse("2019-11-25T13:09:24.188")))
      )

      PaymentSearchResult.httpReads.read("GET", "testUrl", httpResponse) shouldBe Some(expected)

    }

  }

  "Status is OK but Json is invalid" should {

    "throw an InvalidJsonException" in new LocalSetup {

      val json = Json.parse("""
                              |{
                              |   "searchScope":"PTA",
                              |   "searchTab":"1097172564",
                              |   "payments": [
                              |      {
                              |         "id":"5ddbd2847a0000c7f0d845a4",
                              |         "reference":"1097172564",
                              |         "amountInPence":10623,
                              |         "status":"Successful",
                              |         "createdOn":"2019-11-25T13:09:24.188",
                              |         "taxType":"self-assessment"
                              |      }
                              |   ]
                              |}
                              |
                              |""".stripMargin)

      override val httpResponse: HttpResponse = HttpResponse(200, Some(json))

      an[InvalidJsonException] should be thrownBy {
        PaymentSearchResult.httpReads.read("GET", "testUrl", httpResponse)
      }

    }
  }

  "status is NOT_FOUND" should {

    "return None" in new LocalSetup {

      override val httpResponse: HttpResponse = HttpResponse(404)

      PaymentSearchResult.httpReads.read("GET", "testUrl", httpResponse) shouldBe None
    }
  }

  "status is anything else" should {

    "throw an Upstream5xxResponse" in new LocalSetup {

      override val httpResponse: HttpResponse = HttpResponse(502)

      an[Upstream5xxResponse] should be thrownBy {
        PaymentSearchResult.httpReads.read("GET", "testUrl", httpResponse)
      }

    }
  }
}
