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

package controllers.auth

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, post, urlEqualTo, urlMatching, status => _}
import models.PertaxResponse
import models.admin._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class AuthJourneyItSpec extends IntegrationSpec {

  val url = "/personal-account"

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.pertax.port" -> server.port()
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    server.stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(aResponse().withBody(authResponse))
    )
    server.stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$generatedNino"))
        .willReturn(ok(citizenResponse))
    )

    server.stubFor(
      get(urlMatching("/messages/count.*"))
        .willReturn(ok("{}"))
    )

    server.stubFor(
      post(urlEqualTo("/pertax/authorise"))
        .willReturn(
          aResponse()
            .withBody("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}")
        )
    )

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PaperlessInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(PaperlessInterruptToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, isEnabled = true)))

  }

  "personal-account" must {
    "allow the user to progress to the service" when {
      "Pertax returns ACCESS_GRANTED" in {
        val pertaxResponse = Json
          .toJson(
            PertaxResponse("ACCESS_GRANTED", "Access granted", None, None)
          )
          .toString

        server.stubFor(
          post(urlEqualTo("/pertax/authorise"))
            .willReturn(aResponse().withBody(pertaxResponse))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer 1")
        val result  = route(app, request).get

        status(result) mustBe OK
      }
    }

    "redirect" when {
      "Pertax returns NO_HMRC_PT_ENROLMENT" in {
        val tenBaseUrl     = "http://localhost:7750/ten"
        val pertaxResponse = Json
          .toJson(
            PertaxResponse(
              "NO_HMRC_PT_ENROLMENT",
              "There is no valid HMRC PT enrolment",
              None,
              Some(tenBaseUrl)
            )
          )
          .toString

        server.stubFor(
          post(urlEqualTo("/pertax/authorise"))
            .willReturn(aResponse().withBody(pertaxResponse))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer 1")
        val result  = route(app, request).get

        status(result) mustBe SEE_OTHER
        redirectLocation(
          result
        ) mustBe Some(
          s"$tenBaseUrl?redirectUrl=%2Fpersonal-account"
        )
      }
    }

    "pertax returning an unsupported code" must {
      "trigger an internal error page" in {
        val pertaxResponse = Json
          .toJson(
            PertaxResponse(
              "INVALID_CODE",
              "Invalid",
              None,
              None
            )
          )
          .toString

        server.stubFor(
          post(urlEqualTo("/pertax/authorise"))
            .willReturn(aResponse().withBody(pertaxResponse))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer 1")
        val result  = route(app, request).get

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(
          result
        ) must include("Sorry, the service is unavailable")
      }
    }
  }
}
