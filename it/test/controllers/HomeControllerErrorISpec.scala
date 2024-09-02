/*
 * Copyright 2024 HM Revenue & Customs
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

package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.UserAnswers
import play.api.Application
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future

class HomeControllerErrorISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"   -> true,
      "microservice.services.taxcalc-frontend.port" -> server.port(),
      "microservice.services.tai.port"              -> server.port()
    )
    .build()

  val url = s"/personal-account"

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController(auth = false, memorandum = false)
  }

  "personal-account" must {
    "show an error view" in {
      val expectedMessage = "<<<It works!>>>"
      server.stubFor(
        post(urlEqualTo("/pertax/authorise"))
          .willReturn(
            aResponse()
              .withBody("""{
                  | "code": "INVALID_AFFINITY",
                  | "message": "The user is neither an individual or an organisation",
                  | "errorView": {
                  |   "url": "/partials/view",
                  |   "statusCode": 403
                  | }
                  |}""".stripMargin)
          )
      )

      server.stubFor(
        get(urlEqualTo("/partials/view"))
          .willReturn(
            aResponse()
              .withBody(expectedMessage)
          )
      )

      val authResponseNoHmrcPt =
        s"""
           |{
           |    "confidenceLevel": 50,
           |    "nino": "$generatedNino",
           |    "name": {
           |        "name": "John",
           |        "lastName": "Smith"
           |    },
           |    "loginTimes": {
           |        "currentLogin": "2021-06-07T10:52:02.594Z",
           |        "previousLogin": null
           |    },
           |    "optionalCredentials": {
           |        "providerId": "4911434741952698",
           |        "providerType": "GovernmentGateway"
           |    },
           |    "authProviderId": {
           |        "ggCredId": "xyz"
           |    },
           |    "externalId": "testExternalId",
           |    "allEnrolments": [],
           |    "affinityGroup": "Agent",
           |    "credentialStrength": "strong"
           |}
           |""".stripMargin

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponseNoHmrcPt)))
      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(serverError())
      )
      server.stubFor(
        put(urlMatching(s"/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(UserAnswers.empty("id")).toString))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe FORBIDDEN
      contentAsString(result) must include(expectedMessage)
      server.verify(0, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(0, getRequestedFor(urlEqualTo("/tax-you-paid/summary-card-partials")))

    }

    "redirect to protect-tax-info if HMRC-PT enrolment is not present" in {
      val authResponseNoHmrcPt =
        s"""
           |{
           |    "confidenceLevel": 200,
           |    "nino": "$generatedNino",
           |    "name": {
           |        "name": "John",
           |        "lastName": "Smith"
           |    },
           |    "loginTimes": {
           |        "currentLogin": "2021-06-07T10:52:02.594Z",
           |        "previousLogin": null
           |    },
           |    "optionalCredentials": {
           |        "providerId": "4911434741952698",
           |        "providerType": "GovernmentGateway"
           |    },
           |    "authProviderId": {
           |        "ggCredId": "xyz"
           |    },
           |    "externalId": "testExternalId",
           |    "allEnrolments": [],
           |    "affinityGroup": "Individual",
           |    "credentialStrength": "strong"
           |}
           |""".stripMargin

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponseNoHmrcPt)))
      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(serverError())
      )
      server.stubFor(
        put(urlMatching(s"/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(UserAnswers.empty("id")).toString))
      )

      server.stubFor(
        post(urlEqualTo("/pertax/authorise"))
          .willReturn(
            aResponse()
              .withBody("""{
                  | "code": "NO_HMRC_PT_ENROLMENT",
                  | "message": "There is no valid HMRC PT enrolment",
                  | "redirect": "https://example.com/redirect"
                  |}""".stripMargin)
          )
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("https://example.com/redirect?redirectUrl=%2Fpersonal-account")
      server.verify(0, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(0, getRequestedFor(urlEqualTo("/tax-you-paid/summary-card-partials")))
    }
  }
}
