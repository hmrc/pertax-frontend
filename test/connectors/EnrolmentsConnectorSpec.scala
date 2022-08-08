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

import play.api.Application
import play.api.libs.json.JsResultException
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import testUtils.WireMockHelper
import uk.gov.hmrc.http.BadRequestException

class EnrolmentsConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout {

  val baseUrl: String = "/enrolment-store-proxy"

  override lazy val app: Application = app(
    Map("microservice.services.enrolment-store-proxy.port" -> server.port())
  )

  def connector: EnrolmentsConnector = app.injector.instanceOf[EnrolmentsConnector]

  "getAssignedEnrolments" must {
    val utr = "1234500000"
    val url = s"$baseUrl/enrolment-store/enrolments/IR-SA~UTR~$utr/users"

    "Return the error message for a BAD_REQUEST response" in {
      //TODO: Check this scenario (it does not match the old test)
      stubGet(url, BAD_REQUEST, None)
      lazy val result = await(connector.getUserIdsWithEnrolments(utr))
      a[BadRequestException] mustBe thrownBy(result)
    }

    "NO_CONTENT response should return no enrolments" in {
      stubGet(url, NO_CONTENT, None)
      val result = connector.getUserIdsWithEnrolments(utr).futureValue

      result mustBe a[Right[_, _]]
      result.right.get mustBe empty
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
      val result = connector.getUserIdsWithEnrolments(utr).futureValue

      result mustBe a[Right[_, _]]
      result.right.get mustBe empty
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
      val result = connector.getUserIdsWithEnrolments(utr).futureValue

      result mustBe a[Right[_, _]]
      result.right.get must contain.allElementsOf(expected)
    }
  }
}
