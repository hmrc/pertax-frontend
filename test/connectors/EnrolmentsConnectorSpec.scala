/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import util.{BaseSpec, NullMetrics}

import scala.concurrent.Future

class EnrolmentsConnectorSpec extends BaseSpec with EitherValues {

  val http = mock[DefaultHttpClient]
  val connector = new EnrolmentsConnector(http, config, new NullMetrics)
  val baseUrl = config.enrolmentStoreProxyUrl

  "getAssignedEnrolments" must {
    val utr = "1234500000"
    val url = s"$baseUrl/enrolment-store/enrolments/IR-SA~UTR~$utr/users"

    "Return the error message for a BAD_REQUEST response" in {
      when(http.GET[HttpResponse](eqTo(url), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

      connector.getUserIdsWithEnrolments(utr).futureValue.left.value must include(BAD_REQUEST.toString)
    }

    "NO_CONTENT response should return no enrolments" in {
      when(http.GET[HttpResponse](eqTo(url), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      connector.getUserIdsWithEnrolments(utr).futureValue.right.value mustBe Seq.empty
    }

    "query users with no principal enrolment returns empty enrolments" in {
      val json = Json.parse("""
                              |{
                              |    "principalUserIds": [],
                              |     "delegatedUserIds": []
                              |}""".stripMargin)

      when(http.GET[HttpResponse](eqTo(url), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(json))))

      connector.getUserIdsWithEnrolments(utr).futureValue.right.value mustBe Seq.empty
    }

    "query users with assigned enrolment return two principleIds" in {
      val json = Json.parse("""
                              |{
                              |    "principalUserIds": [
                              |       "ABCEDEFGI1234567",
                              |       "ABCEDEFGI1234568"
                              |    ],
                              |    "delegatedUserIds": [
                              |     "dont care"
                              |    ]
                              |}""".stripMargin)

      when(http.GET[HttpResponse](eqTo(url), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(json))))

      val expected = Seq("ABCEDEFGI1234567", "ABCEDEFGI1234568")

      connector.getUserIdsWithEnrolments(utr).futureValue.right.value mustBe expected
    }
  }
}
