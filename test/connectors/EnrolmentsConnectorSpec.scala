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

package connectors

import play.api.Application
import play.api.test.DefaultAwaitTimeout
import testUtils.WireMockHelper
import uk.gov.hmrc.http.UpstreamErrorResponse

class EnrolmentsConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout {

  val baseUrl: String = "/enrolment-store-proxy"

  override lazy val app: Application = app(
    Map("microservice.services.enrolment-store-proxy.port" -> server.port())
  )

  def connector: EnrolmentsConnector = app.injector.instanceOf[EnrolmentsConnector]

  "getAssignedEnrolments" must {
    val utr = "1234500000"
    val url = s"$baseUrl/enrolment-store/enrolments/IR-SA~UTR~$utr/users"

    "BAD_REQUEST response should return Left BAD_REQUEST status" in {
      stubGet(url, BAD_REQUEST, None)
      lazy val result = connector.getUserIdsWithEnrolments(utr).value.futureValue
      result.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe BAD_REQUEST
    }

    "NO_CONTENT response should return no enrolments" in {
      stubGet(url, NO_CONTENT, None)
      val result = connector.getUserIdsWithEnrolments(utr).value.futureValue

      result mustBe a[Right[_, _]]
      result.getOrElse(Seq("", "")) mustBe empty
    }

    "query users with no principal enrolment returns empty enrolments" in {
      val json =
        """
          |{
          |    "principalUserIds": [],
          |     "delegatedUserIds": []
          |}
        """.stripMargin

      stubGet(url, OK, Some(json))
      val result = connector.getUserIdsWithEnrolments(utr).value.futureValue

      result mustBe a[Right[_, _]]
      result.getOrElse(Seq("", "")) mustBe empty
    }

    "query users with assigned enrolment return two principleIds" in {
      val json =
        """
          |{
          |    "principalUserIds": [
          |       "ABCEDEFGI1234567",
          |       "ABCEDEFGI1234568"
          |    ],
          |    "delegatedUserIds": [
          |     "dont care"
          |    ]
          |}
        """.stripMargin

      stubGet(url, OK, Some(json))

      val expected = Seq("ABCEDEFGI1234567", "ABCEDEFGI1234568")

      stubGet(url, OK, Some(json))
      val result = connector.getUserIdsWithEnrolments(utr).value.futureValue

      result mustBe a[Right[_, _]]
      result.getOrElse(Seq("", "")) must contain.allElementsOf(expected)
    }
  }
}
