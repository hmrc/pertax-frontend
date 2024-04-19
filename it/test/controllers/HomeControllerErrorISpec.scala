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
import models.admin.SingleAccountCheckToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, defaultAwaitTimeout, redirectLocation, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future

class HomeControllerErrorISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.taxcalc-frontend.port" -> server.port(),
      "microservice.services.tai.port"              -> server.port(),
      "feature.business-hours.0.day"                -> "Monday",
      "feature.business-hours.0.start-time"         -> "0:00",
      "feature.business-hours.0.end-time"           -> "23:59",
      "feature.business-hours.1.day"                -> "Tuesday",
      "feature.business-hours.1.start-time"         -> "0:00",
      "feature.business-hours.1.end-time"           -> "23:59",
      "feature.business-hours.2.day"                -> "Wednesday",
      "feature.business-hours.2.start-time"         -> "0:00",
      "feature.business-hours.2.end-time"           -> "23:59",
      "feature.business-hours.3.day"                -> "Thursday",
      "feature.business-hours.3.start-time"         -> "0:00",
      "feature.business-hours.3.end-time"           -> "23:59",
      "feature.business-hours.4.day"                -> "Friday",
      "feature.business-hours.4.start-time"         -> "0:00",
      "feature.business-hours.4.end-time"           -> "23:59",
      "feature.business-hours.5.day"                -> "Saturday",
      "feature.business-hours.5.start-time"         -> "0:00",
      "feature.business-hours.5.end-time"           -> "23:59",
      "feature.business-hours.6.day"                -> "Sunday",
      "feature.business-hours.6.start-time"         -> "0:00",
      "feature.business-hours.6.end-time"           -> "23:59"
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

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, isEnabled = true)))
  }

  "personal-account" must {
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
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("http://localhost:7750/protect-tax-info?redirectUrl=%2Fpersonal-account")
      server.verify(0, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(0, getRequestedFor(urlEqualTo("/tax-you-paid/summary-card-partials")))
    }
  }
}
