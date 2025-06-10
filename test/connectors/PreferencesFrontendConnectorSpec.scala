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

import com.codahale.metrics.Timer.Context
import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import com.github.tomakehurst.wiremock.client.WireMock._
import controllers.auth.requests.UserRequest
import models._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.http.ContentTypes
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsResultException
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.CONTENT_TYPE
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{BaseSpec, FileHelper, WireMockHelper}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.HttpErrorFunctions.{is4xx, is5xx}
import uk.gov.hmrc.http.{HttpResponse, SessionKeys, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

class PreferencesFrontendConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  val mockMetrics: Metrics               = mock[Metrics]
  val mockMetricRegistry: MetricRegistry = mock[MetricRegistry]
  val mockTimer: Timer                   = mock[Timer]
  val mockContext: Context               = mock[Context]
  val mockCounter: Counter               = mock[Counter]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[Metrics].toInstance(mockMetrics))
    .configure("microservice.services.preferences-frontend.port" -> server.port)
    .build()

  when(mockMetrics.defaultRegistry).thenReturn(mockMetricRegistry)

  when(mockMetricRegistry.timer(anyString())).thenReturn(mockTimer)

  when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter)

  when(mockTimer.time()).thenReturn(mockContext)

  when(mockContext.stop()).thenReturn(1L)

  "PreferencesFrontend" must {

    "return None if an OK status is retrieved" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      implicit val connector: PreferencesFrontendConnector = app.injector.instanceOf[PreferencesFrontendConnector]

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
              .withStatus(OK)
              .withBody(jsonBody)
          )
      )

      val result = connector.getPaperlessPreference().value.futureValue.getOrElse(HttpResponse(BAD_REQUEST, ""))

      result.status mustBe OK
    }

    "return a redirectUrl if Precondition failed with 412 response" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest().withSession(SessionKeys.authToken -> "Bearer 1")
        )

      implicit val connector: PreferencesFrontendConnector = app.injector.instanceOf[PreferencesFrontendConnector]

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
          )
      )

      val result =
        connector.getPaperlessPreference().value.futureValue.getOrElse(HttpResponse.apply(BAD_REQUEST, jsonBody))

      result.status mustBe PRECONDITION_FAILED
      result.body must include("http://www.testurl.com")
    }

    List(
      BAD_REQUEST,
      NOT_FOUND,
      TOO_MANY_REQUESTS,
      REQUEST_TIMEOUT,
      INTERNAL_SERVER_ERROR,
      SERVICE_UNAVAILABLE,
      BAD_GATEWAY
    ).foreach { errorResponse =>
      s"return UpstreamErrorResponse when the connector retrieves a $errorResponse status" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
            credentials = Credentials("", "GovernmentGateway"),
            confidenceLevel = ConfidenceLevel.L200,
            request = FakeRequest()
          )

        implicit val connector: PreferencesFrontendConnector = app.injector.instanceOf[PreferencesFrontendConnector]

        val url = "/paperless/activate"

        val jsonBody =
          """{}""".stripMargin

        server.stubFor(
          put(urlMatching(s"$url.*"))
            .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
            .willReturn(
              aResponse()
                .withStatus(errorResponse)
                .withBody(jsonBody)
            )
        )

        val result = connector.getPaperlessPreference().value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))

        result.statusCode mustBe errorResponse
      }
    }

    "return a PaperlessStatusResponse with status ALRIGHT" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      implicit val connector: PreferencesFrontendConnector = app.injector.instanceOf[PreferencesFrontendConnector]

      val url = "/paperless/status"

      server.stubFor(
        get(urlMatching(s"$url.*"))
          .willReturn(
            ok(FileHelper.loadFile("./test/resources/paperless-status/PaperlessStatusAlright.json"))
          )
      )

      val result = connector
        .getPaperlessStatus(s"$url/redirect", "returnMessage")
        .value
        .futureValue

      result mustBe a[Right[_, PaperlessMessagesStatus]]

      result.getOrElse(PaperlessStatusNewCustomer()) mustBe a[PaperlessStatusOptIn]
    }
    "return a PaperlessStatusResponse with status Bounced" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      implicit val connector: PreferencesFrontendConnector = app.injector.instanceOf[PreferencesFrontendConnector]

      val url = "/paperless/status"

      server.stubFor(
        get(urlMatching(s"$url.*"))
          .willReturn(
            ok(FileHelper.loadFile("./test/resources/paperless-status/PaperlessStatusBounced.json"))
          )
      )

      val result = connector
        .getPaperlessStatus(s"$url/redirect", "returnMessage")
        .value
        .futureValue

      result mustBe a[Right[_, PaperlessMessagesStatus]]

      result.getOrElse(PaperlessStatusNewCustomer()) mustBe a[PaperlessStatusBounced]
    }

    List(
      BAD_REQUEST,
      UNAUTHORIZED,
      FORBIDDEN
    ).foreach { error =>
      s"return a UpstreamErrorResponse status 4xx when PreferencesFrontend returns a $error response" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
            request = FakeRequest()
          )

        implicit val connector: PreferencesFrontendConnector = app.injector.instanceOf[PreferencesFrontendConnector]

        val url = "/paperless/status"

        server.stubFor(
          get(urlMatching(s"$url.*"))
            .willReturn(
              aResponse().withStatus(error)
            )
        )

        val result = connector
          .getPaperlessStatus(s"$url/redirect", "returnMessage")
          .value
          .futureValue

        result mustBe a[Left[_, _]]

        is4xx(result.swap.getOrElse(UpstreamErrorResponse("Error", INTERNAL_SERVER_ERROR)).statusCode) mustBe true
      }
    }

    List(
      INTERNAL_SERVER_ERROR,
      SERVICE_UNAVAILABLE
    ).foreach { error =>
      s"return a UpstreamErrorResponse status 5xx when PreferencesFrontend returns a $error response" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
            request = FakeRequest()
          )

        implicit val connector: PreferencesFrontendConnector = app.injector.instanceOf[PreferencesFrontendConnector]

        val url = "/paperless/status"

        server.stubFor(
          get(urlMatching(s"$url.*"))
            .willReturn(
              aResponse().withStatus(error)
            )
        )

        val result = connector
          .getPaperlessStatus(s"$url/redirect", "returnMessage")
          .value
          .futureValue

        result mustBe a[Left[_, _]]

        is5xx(result.swap.getOrElse(UpstreamErrorResponse("Error", BAD_REQUEST)).statusCode) mustBe true
      }
    }

    "return a PaperlessStatusFailed if invalid json is returned" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      implicit val connector: PreferencesFrontendConnector = app.injector.instanceOf[PreferencesFrontendConnector]

      val url = "/paperless/status"

      val json =
        """
          |{
          |    "principalUserIds": [],
          |     "delegatedUserIds": []
          |}
        """.stripMargin

      server.stubFor(
        get(urlMatching(s"$url.*"))
          .willReturn(
            ok(json)
          )
      )

      val result = connector
        .getPaperlessStatus(s"$url/redirect", "returnMessage")
        .value

      whenReady(result.failed) { ex =>
        ex mustBe a[JsResultException]
      }
    }
  }
}
