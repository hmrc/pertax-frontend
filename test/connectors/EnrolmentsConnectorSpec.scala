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

import cats.data.EitherT
import config.ConfigDecorator
import models.enrolments.EnrolmentEnum.IRSAKey
import models.enrolments.{EACDEnrolment, IdentifiersOrVerifiers, KnownFactQueryForNINO, KnownFactResponseForNINO}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import play.api.Application
import play.api.libs.json.Json
import play.api.test._
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HttpReads, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.Future
import scala.util.Random

class EnrolmentsConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout with Injecting {

  val baseUrl: String = "/enrolment-store-proxy"

  override lazy val app: Application = app(
    Map("microservice.services.enrolment-store-proxy.port" -> server.port())
  )

  private val mockHttpClientResponse: HttpClientResponse = mock[HttpClientResponse]

  private val mockHttpV2Client: HttpClientV2 = mock[HttpClientV2]

  private val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  private val dummyContent = "error message"

  def connector: EnrolmentsConnector = app.injector.instanceOf[EnrolmentsConnector]

  private val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  override def beforeEach(): Unit = {
    super.beforeEach()
    org.mockito.MockitoSugar.reset(mockHttpClientResponse, mockHttpV2Client, mockConfigDecorator)
  }

  "getUserIdsWithEnrolments" must {
    val utr = "1234500000"
    val url = s"$baseUrl/enrolment-store/enrolments/IR-SA~UTR~$utr/users"

    "BAD_REQUEST response should return Left BAD_REQUEST status" in {

      when(mockConfigDecorator.enrolmentStoreProxyUrl).thenReturn("http://localhost")

      when(mockHttpClientResponse.read(any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future(Left(UpstreamErrorResponse(dummyContent, BAD_REQUEST)))
        )
      )

      when(mockHttpV2Client.get(any())(any())).thenReturn(mockRequestBuilder)

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))

      def enrolmentsConnectorWithMock: EnrolmentsConnector = new EnrolmentsConnector(
        mockHttpV2Client,
        mockConfigDecorator,
        mockHttpClientResponse
      )

      lazy val result = enrolmentsConnectorWithMock.getUserIdsWithEnrolments("IR-SA~UTR", utr).value.futureValue
      result.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe BAD_REQUEST
    }

    "NO_CONTENT response should return no enrolments" in {
      stubGet(url, NO_CONTENT, None)
      val result = connector.getUserIdsWithEnrolments("IR-SA~UTR", utr).value.futureValue

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
      val result = connector.getUserIdsWithEnrolments("IR-SA~UTR", utr).value.futureValue

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
      val result = connector.getUserIdsWithEnrolments("IR-SA~UTR", utr).value.futureValue

      result mustBe a[Right[_, _]]
      result.getOrElse(Seq("", "")) must contain.allElementsOf(expected)
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

      lazy val expectedResult = KnownFactResponseForNINO(
        "IR-SA",
        List(
          EACDEnrolment(
            identifiers = List(IdentifiersOrVerifiers("NINO", testNino.toString())),
            verifiers = List(IdentifiersOrVerifiers("UTR", "123456789"), IdentifiersOrVerifiers("MTDITID", mtdValue))
          )
        )
      )

      val url         = "/enrolment-store-proxy/enrolment-store/enrolments"
      val requestBody = Json.toJson(KnownFactQueryForNINO.apply(testNino, IRSAKey.toString)).toString()

      stubPost(url, OK, Some(requestBody), Some(expectedJson))

      val result = connector.getKnownFacts(nino = testNino).value.futureValue
      result mustBe a[Right[_, _]]
      result.getOrElse(None) mustBe Some(expectedResult)
    }
    "return None when enrolment store gives no content" in {
      val generator = new Generator(new Random())

      val testNino = generator.nextNino

      val url         = "/enrolment-store-proxy/enrolment-store/enrolments"
      val requestBody = Json.toJson(KnownFactQueryForNINO.apply(testNino, IRSAKey.toString)).toString()

      stubPost(url, NO_CONTENT, Some(requestBody), None)

      val result = connector.getKnownFacts(nino = testNino).value.futureValue
      result mustBe a[Right[_, _]]
      result.getOrElse(Some("invalid result")) mustBe None
    }

    "return None when enrolment store gives an unexpected response of status < 400" in {
      val generator = new Generator(new Random())

      val testNino = generator.nextNino

      val url         = "/enrolment-store-proxy/enrolment-store/enrolments"
      val requestBody = Json.toJson(KnownFactQueryForNINO.apply(testNino, IRSAKey.toString)).toString()

      stubPost(url, 1, Some(requestBody), None)

      val result = connector.getKnownFacts(nino = testNino).value.futureValue
      result mustBe a[Right[_, _]]
      result.getOrElse(Some("invalid result")) mustBe None
    }
  }
}
