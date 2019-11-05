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

package connectors

import models._
import org.joda.time.DateTime
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.Inspectors.forAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsResultException, Json}
import services.http.WsAllMethods
import uk.gov.hmrc.http.{HttpException, HttpResponse}
import util.BaseSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrolmentsConnectorSpec extends BaseSpec with MockitoSugar with ScalaFutures {

  val http = mock[WsAllMethods]
  val connector = new EnrolmentsConnector(http, config)
  val baseUrl = config.enrolmentStoreProxyUrl

  "getAssignedEnrolments" should {
    val utr = "1234500000"
    val url = s"$baseUrl/enrolment-store/enrolments/IR-SA~UTR~$utr/users?type=principal"

    "throw a return no enrolments for a BAD_REQUEST response" in {
      when(http.GET[HttpResponse](eqTo(url))(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

      val f = connector.getUserIdsWithEnrolments(utr)
      whenReady(f.failed) { e =>
        e shouldBe a[HttpException]
      }
    }

    "throws a JsResultException when given bad json" in {
      val badJson = Json.obj("abc" -> "invalidData")

      when(http.GET[HttpResponse](eqTo(url))(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(badJson))))

      val f = connector.getUserIdsWithEnrolments(utr)
      whenReady(f.failed) { e =>
        e shouldBe a[JsResultException]
      }
    }

    "NO_CONTENT response should return no enrolments" in {
      when(http.GET[HttpResponse](eqTo(url))(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      connector.getUserIdsWithEnrolments(utr).futureValue shouldBe Seq.empty
    }

    "query users with no principal enrolment returns empty enrolments" in {
      val json = Json.parse("""
                              |{
                              |    "principalUserIds": []
                              |}""".stripMargin)

      when(http.GET[HttpResponse](eqTo(url))(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(json))))

      connector.getUserIdsWithEnrolments(utr).futureValue shouldBe Seq.empty
    }

    "query users with assigned enrolment return two principleIds" in {
      val json = Json.parse("""
                              |{
                              |    "principalUserIds": [
                              |       "ABCEDEFGI1234567",
                              |       "ABCEDEFGI1234568"
                              |    ]
                              |}""".stripMargin)

      when(http.GET[HttpResponse](eqTo(url))(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(json))))

      val expected = Seq("ABCEDEFGI1234567", "ABCEDEFGI1234568")

      connector.getUserIdsWithEnrolments(utr).futureValue shouldBe expected
    }
  }
}
