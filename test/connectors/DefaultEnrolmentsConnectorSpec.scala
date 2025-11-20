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

import models.enrolments.EnrolmentEnum.IRSAKey
import models.enrolments.{EACDEnrolment, IdentifiersOrVerifiers, KnownFactsRequest, KnownFactsResponse}
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.*
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.util.Random

class DefaultEnrolmentsConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout with Injecting {

  val baseUrl: String = "/enrolment-store-proxy"

  override lazy val app: Application = app(
    Map("microservice.services.enrolment-store-proxy.port" -> server.port())
  )

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  def connector: DefaultEnrolmentsConnector = app.injector.instanceOf[DefaultEnrolmentsConnector]

  "getUserIdsWithEnrolments" must {
    val utr = "1234500000"
    val url = s"$baseUrl/enrolment-store/enrolments/IR-SA~UTR~$utr/users"

    "NO_CONTENT response should return no enrolments" in {
      stubGet(url, NO_CONTENT, None)
      val result = connector.getUserIdsWithEnrolments(s"IR-SA~UTR~$utr").value.futureValue

      result mustBe Right(Seq.empty)
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
      val result = connector.getUserIdsWithEnrolments(s"IR-SA~UTR~$utr").value.futureValue

      result mustBe Right(expected)
    }
  }

  "getKnownFacts" must {
    "return a Some(VALUE) of MTDITD enrolments relating to NINO" in {
      val generator = new Generator(new Random())

      val testNino: Nino = generator.nextNino

      val mtdValue = "Example Enrolment Value"

      val expectedJson =
        s"""{
           |    "service": "IR-SA",
           |    "enrolments": [{
           |        "identifiers": [{
           |            "key": "NINO",
           |            "value": "$testNino"
           |        }],
           |        "verifiers": [{
           |            "key": "UTR",
           |            "value": "123456789"
           |        },
           |        {
           |            "key": "MTDITID",
           |            "value": "$mtdValue"
           |        }]
           |    }]
           |}""".stripMargin

      val knownFactsRequest = KnownFactsRequest("IR-SA", List(IdentifiersOrVerifiers("NINO", testNino.nino)))
      lazy val expectedResult = KnownFactsResponse(
        "IR-SA",
        List(
          EACDEnrolment(
            identifiers = List(IdentifiersOrVerifiers("NINO", testNino.toString())),
            verifiers = List(IdentifiersOrVerifiers("UTR", "123456789"), IdentifiersOrVerifiers("MTDITID", mtdValue))
          )
        )
      )

      val url         = "/enrolment-store-proxy/enrolment-store/enrolments"
      val requestBody = Json.toJson(KnownFactsRequest.apply("IR-SA",List(IdentifiersOrVerifiers("NINO", testNino.nino)))).toString()

      stubPost(url, OK, Some(requestBody), Some(expectedJson))

      val result = connector.getKnownFacts(knownFactsRequest).value.futureValue
      result mustBe Right(expectedResult)
    }
    "return empty response when enrolment store gives no content" in {
      val generator = new Generator(new Random())

      val testNino = generator.nextNino

      val url         = "/enrolment-store-proxy/enrolment-store/enrolments"
      val requestBody = Json.toJson(KnownFactsRequest.apply("IR-SA",List(IdentifiersOrVerifiers("NINO", testNino.nino)))).toString()

      val knownFactsRequest = KnownFactsRequest("IR-SA", List(IdentifiersOrVerifiers("NINO", testNino.nino)))

      stubPost(url, NO_CONTENT, Some(requestBody), None)

      val result = connector.getKnownFacts(knownFactsRequest).value.futureValue
      result mustBe Right(KnownFactsResponse(IRSAKey.toString, List.empty))
    }

  }
}
