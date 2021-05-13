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

package services

import com.codahale.metrics.Timer.Context
import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import com.github.tomakehurst.wiremock.client.WireMock._
import com.kenshoo.play.metrics.Metrics
import controllers.auth.requests.UserRequest
import models._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.Application
import play.api.http.ContentTypes
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.CONTENT_TYPE
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import util.UserRequestFixture.buildUserRequest
import util.{BaseSpec, WireMockHelper}

class PreferencesFrontendServiceSpec extends BaseSpec with WireMockHelper {

  val mockMetrics = mock[Metrics]
  val mockMetricRegistry = mock[MetricRegistry]
  val mockTimer = mock[Timer]
  val mockContext = mock[Context]
  val mockCounter = mock[Counter]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[Metrics].toInstance(mockMetrics))
    .configure("microservice.services.preferences-frontend.port" -> server.port)
    .build()

  when(mockMetrics.defaultRegistry).thenReturn(mockMetricRegistry)

  when(mockMetricRegistry.timer(anyString())).thenReturn(mockTimer)

  when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter)

  when(mockTimer.time()).thenReturn(mockContext)

  when(mockContext.stop()).thenReturn(1L)

  //TODO: Find a way to mock metrics in a testable way
  "PreferencesFrontend" must {

    "return ActivatePaperlessActivatedResponse if it is successful, and user is Government GateWay" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]

      val url = "/paperless/activate"

      val jsonBody =
        """{
          | "redirectUserTo": "/foo"
          |}
          |""".stripMargin

      server.stubFor(
        put(urlMatching(s"$url.*"))
          .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(jsonBody)
          ))

      val result = service.getPaperlessPreference().futureValue

      result mustBe ActivatePaperlessActivatedResponse

    }

    "return ActivatePaperlessNotAllowedResponse if user is not Government Gateway" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          credentials = Credentials("", "Verify"),
          confidenceLevel = ConfidenceLevel.L500,
          request = FakeRequest()
        )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]

      val result = service.getPaperlessPreference().futureValue

      result mustBe ActivatePaperlessNotAllowedResponse

    }

    "return ActivatePaperlessNotAllowedResponse if any upstream exceptions are thrown" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          credentials = Credentials("", "Verify"),
          confidenceLevel = ConfidenceLevel.L500,
          request = FakeRequest()
        )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]

      val url = "/paperless/activate"

      server.stubFor(
        put(urlMatching(s"$url.*"))
          .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
          .willReturn(
            aResponse()
              .withStatus(303)
          ))

      val result = service.getPaperlessPreference().futureValue

      result mustBe ActivatePaperlessNotAllowedResponse
    }

    "return ActivatePaperlessNotAllowedResponse if BadRequestException is thrown" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          credentials = Credentials("", "Verify"),
          confidenceLevel = ConfidenceLevel.L500,
          request = FakeRequest()
        )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]

      val url = "/paperless/activate"

      server.stubFor(
        put(urlMatching(s"$url.*"))
          .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
          .willReturn(
            aResponse()
              .withStatus(400)
          ))

      val result = service.getPaperlessPreference().futureValue

      result mustBe ActivatePaperlessNotAllowedResponse
    }

    "return ActivatePaperlessRequiresUserActionResponse if Precondition failed with 412 response" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]

      val url = "/paperless/activate"

      val jsonBody =
        """{
          | "redirectUserTo": "http://www.testurl.com"
          |}
          |""".stripMargin

      server.stubFor(
        put(urlMatching(s"$url.*"))
          .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
          .willReturn(
            aResponse()
              .withStatus(PRECONDITION_FAILED)
              .withBody(jsonBody)
          ))

      val result = service.getPaperlessPreference().futureValue

      result mustBe ActivatePaperlessRequiresUserActionResponse("http://www.testurl.com")
    }

    "return ActivatePaperlessNotAllowedResponse when called and service is down" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          credentials = Credentials("", "Verify"),
          confidenceLevel = ConfidenceLevel.L500,
          request = FakeRequest()
        )

      implicit val service = app.injector.instanceOf[PreferencesFrontendService]

      val url = "/paperless/activate"

      server.stubFor(
        put(urlMatching(s"$url.*"))
          .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
          .willReturn(
            aResponse()
              .withStatus(500)
          ))

      val result = service.getPaperlessPreference().futureValue

      result mustBe ActivatePaperlessNotAllowedResponse
    }
  }
}
