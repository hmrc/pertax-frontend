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
import com.github.tomakehurst.wiremock.http.{HttpHeader, HttpHeaders}
import models.{ActivatedOnlineFilerSelfAssessmentUser, ErrorView, PertaxResponse, UserDetails, UserName}
import models.admin._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Writeable.wByteArray
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{Fixtures, IntegrationSpec}
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
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

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PertaxBackendToggle)))
      .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, isEnabled = true)))
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

    "Agent" when {
      "be shown a forbidden screen" in {
        val pageStatus  = FORBIDDEN
        val pageContent = "<h1>Some Html content</h1>"
        val partialUrl  = "/partials/view"

        val pertaxResponse = Json
          .toJson(
            PertaxResponse(
              "INVALID_AFFINITY",
              "The user is neither an individual or an organisation",
              Some(ErrorView(partialUrl, pageStatus)),
              None
            )
          )
          .toString

        server.stubFor(
          post(urlEqualTo("/pertax/authorise"))
            .willReturn(aResponse().withBody(pertaxResponse))
        )

        server.stubFor(
          get(urlEqualTo(s"$partialUrl"))
            .willReturn(
              aResponse()
                .withHeaders(
                  new HttpHeaders(
                    new HttpHeader("x-title", "title"),
                    new HttpHeader("content-type", "text/html; charset=UTF-8")
                  )
                )
                .withBody(pageContent)
            )
        )

        val fakeRequest = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer 1")
        val request     = buildUserRequest(
          authNino = Fixtures.fakeNino,
          nino = Some(Fixtures.fakeNino),
          userName = Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
          credentials = Credentials("", UserDetails.GovernmentGatewayAuthProvider),
          confidenceLevel = ConfidenceLevel.L200,
          personDetails = Some(Fixtures.buildPersonDetails),
          trustedHelper = None,
          profile = None,
          enrolments = Set(
            Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", new SaUtrGenerator().nextSaUtr.utr)), "Activated")
          ),
          request = fakeRequest
        )

        val result = route(app, request).get

        status(result) mustBe pageStatus
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
