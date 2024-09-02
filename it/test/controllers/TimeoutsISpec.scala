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
import models.admin._
import models.{Person, PersonDetails}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import play.api.Application
import play.api.http.Status.OK
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.time.TaxYear

import java.util.UUID
import scala.concurrent.Future

class TimeoutsISpec extends IntegrationSpec {
  private val timeoutThresholdInMilliseconds  = 50
  private val delayInMilliseconds             = 200
  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"                              -> true,
      "microservice.services.breathing-space-if-proxy.port"                    -> server.port(),
      "microservice.services.breathing-space-if-proxy.timeoutInMilliseconds"   -> timeoutThresholdInMilliseconds,
      "microservice.services.tai.port"                                         -> server.port(),
      "microservice.services.tai.timeoutInMilliseconds"                        -> timeoutThresholdInMilliseconds,
      "microservice.services.taxcalc-frontend.port"                            -> server.port(),
      "microservice.services.taxcalc-frontend.timeoutInMilliseconds"           -> timeoutThresholdInMilliseconds,
      "microservice.services.citizen-details.port"                             -> server.port(),
      "microservice.services.citizen-details.timeoutInMilliseconds"            -> timeoutThresholdInMilliseconds,
      "microservice.services.tcs-broker.port"                                  -> server.port(),
      "microservice.services.tcs-broker.timeoutInMilliseconds"                 -> timeoutThresholdInMilliseconds,
      "microservice.services.dfs-digital-forms-frontend.port"                  -> server.port(),
      "microservice.services.dfs-digital-forms-frontend.timeoutInMilliseconds" -> timeoutThresholdInMilliseconds
    )
    .build()

  private val startTaxYear: Int = TaxYear.current.startYear
  private val dummyContent      = "my body content"
  private val breathingSpaceUrl = s"/$generatedNino/memorandum"
  private val taxComponentsUrl  = s"/tai/$generatedNino/tax-account/$startTaxYear/tax-components"
  private val taxCalcUrl        = "/tax-you-paid/summary-card-partials"
  private val citizenDetailsUrl = s"/citizen-details/$generatedNino/designatory-details"
  private val dfsPartialNinoUrl = "/digital-forms/forms/personal-tax/national-insurance/catalogue"
  private val dfsPartialSAUrl   = "/digital-forms/forms/personal-tax/self-assessment/catalogue"

  private val personDetails: PersonDetails =
    PersonDetails(
      Person(
        Some("Firstname"),
        Some("Middlename"),
        Some("Lastname"),
        None,
        None,
        None,
        None,
        None,
        None
      ),
      None,
      None
    )

  private val authResponseSA: String =
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
       |    "allEnrolments": [
       |       {
       |          "key":"HMRC-PT",
       |          "identifiers": [
       |             {
       |                "key":"NINO",
       |                "value": "$generatedNino"
       |             }
       |          ]
       |       },
       |       {
       |          "key":"IR-SA",
       |          "identifiers": [
       |             {
       |                "key":"UTR",
       |                "value": "$generatedNino"
       |             }
       |          ]
       |       }
       |    ],
       |    "affinityGroup": "Individual",
       |    "credentialStrength": "strong"
       |}
       |""".stripMargin

  private def homePageGET: Future[Result] = route(
    app,
    FakeRequest(GET, "/personal-account")
      .withSession(SessionKeys.sessionId -> UUID.randomUUID().toString, SessionKeys.authToken -> "1")
  ).get

  private val taxCalcPartialContent = "paid-too-much"
  private val taxCalcValidResponse  =
    s"""[{"partialName":"card1","partialContent":"$taxCalcPartialContent"}]
                     |""".stripMargin

  private def getHomePageWithAllTimeouts: Future[Result] = {
    server.stubFor(get(urlPathEqualTo(breathingSpaceUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
    server.stubFor(get(urlEqualTo(taxComponentsUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
    server.stubFor(
      get(urlPathEqualTo(taxCalcUrl)).willReturn(ok(taxCalcValidResponse).withFixedDelay(delayInMilliseconds))
    )
    server.stubFor(get(urlPathEqualTo(citizenDetailsUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
    homePageGET
  }

  override def beforeEach(): Unit = {
    Mockito.reset(mockFeatureFlagService)
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
      .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, isEnabled = true)))
  }

  "/personal-account" must {
    "hide breathing space related components when breathing space connector times out" in {
      val result            = getHomePageWithAllTimeouts
      val content: Document = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("hmrc-caption govuk-caption-xl").get(0).text() mustBe
        Messages("label.this.section.is") + " " + Messages("label.account_home")

      contentAsString(result).contains(Messages("label.breathing_space")) mustBe false
      contentAsString(result).contains(
        controllers.routes.InterstitialController.displayBreathingSpaceDetails.url
      ) mustBe false
      server.verify(1, getRequestedFor(urlEqualTo(breathingSpaceUrl)))
    }

    "show generic marriage allowance content when tax components connector times out" in {
      note(
        "(child benefits & tax credits tiles are always displayed whereas marriage allowance tile differs when times out)"
      )
      val result            = getHomePageWithAllTimeouts
      val content: Document = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("hmrc-caption govuk-caption-xl").get(0).text() mustBe
        Messages("label.this.section.is") + " " + Messages("label.account_home")

      contentAsString(result).contains(
        Messages("label.transfer_part_of_your_personal_allowance_to_your_partner_")
      ) mustBe true
      server.verify(1, getRequestedFor(urlEqualTo(taxComponentsUrl)))
    }

    "hide tax calc elements on page when tax calc connector times out" in {
      val result            = getHomePageWithAllTimeouts
      val content: Document = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("hmrc-caption govuk-caption-xl").get(0).text() mustBe "This section is Account home"

      contentAsString(result).contains(taxCalcPartialContent) mustBe false
      server.verify(1, getRequestedFor(urlEqualTo(taxCalcUrl)))
    }

    "show no person name for citizen details when citizen details connector times out" in {
      val result            = getHomePageWithAllTimeouts
      val content: Document = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("hmrc-caption govuk-caption-xl").get(0).text() mustBe
        "This section is Account home"

      content.getElementsByClass("govuk-heading-xl").get(0).text().isEmpty mustBe true
      server.verify(1, getRequestedFor(urlEqualTo(citizenDetailsUrl)))
    }

    "show correct person name when citizen details connector does NOT time out" in {
      beforeEachHomeController(memorandum = false, matchingDetails = false)
      server.stubFor(get(urlPathEqualTo(breathingSpaceUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
      server.stubFor(get(urlEqualTo(taxComponentsUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
      server.stubFor(get(urlPathEqualTo(taxCalcUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
      server.stubFor(get(urlPathEqualTo(citizenDetailsUrl)).willReturn(ok(Json.toJson(personDetails).toString())))

      val result: Future[Result] = homePageGET
      val content                = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("govuk-heading-xl").get(0).text() mustBe "Firstname Lastname"
      server.verify(1, getRequestedFor(urlEqualTo(citizenDetailsUrl)))
    }
  }

  "/personal-account/your-address/tax-credits-choice" must {
    "render the do you get tax credits page when tax credits broker connector times out" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true)))
      server.stubFor(
        get(urlPathMatching("/keystore/pertax-frontend/.*"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                """{"id": "session-id","data": {"addressPageVisitedDto": {"hasVisitedPage": true}}}""".stripMargin
              )
          )
      )

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(
            ok(
              FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino)
            )
          )
      )
      server.stubFor(
        get(urlPathEqualTo(s"/tcs/$generatedNino/exclusion"))
          .willReturn(aResponse.withFixedDelay(delayInMilliseconds))
      )

      val request = FakeRequest(GET, "/personal-account/your-address/tax-credits-choice")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe OK
      contentAsString(result.get).contains("Do you get tax credits?") mustBe true
    }
  }

  "/personal-account/national-insurance-summary" must {
    "display no NI content when partial connector times out" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(
            ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino))
          )
      )
      server.stubFor(
        get(urlEqualTo(dfsPartialNinoUrl))
          .willReturn(aResponse.withFixedDelay(delayInMilliseconds))
      )

      val request   = FakeRequest(GET, "/personal-account/your-national-insurance-state-pension")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result    = route(app, request).get
      val content   = Jsoup.parse(contentAsString(result))
      val niContent = content.getElementById("national_insurance").text()
      niContent.isEmpty mustBe true
    }

    "display NI content when partial does not time out" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(
            ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino))
          )
      )
      server.stubFor(
        get(urlEqualTo(dfsPartialNinoUrl))
          .willReturn(ok(dummyContent))
      )

      val request   = FakeRequest(GET, "/personal-account/your-national-insurance-state-pension")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result    = route(app, request).get
      val content   = Jsoup.parse(contentAsString(result))
      val niContent = content.getElementById("national_insurance").text()
      niContent mustBe dummyContent
    }
  }

  "/personal-account/self-assessment-summary" must {
    "display no SA content when partial times out" in {
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponseSA)))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(dfsPartialSAUrl))
          .willReturn(aResponse.withFixedDelay(100))
      )

      val request   = FakeRequest(GET, "/personal-account/self-assessment-summary")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result    = route(app, request).get
      val content   = Jsoup.parse(contentAsString(result))
      val niContent = content.getElementById("self-assessment-forms").text()
      niContent.isEmpty mustBe true
    }

    "display SA content when partial does not time out" in {
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponseSA)))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(dfsPartialSAUrl))
          .willReturn(ok(dummyContent))
      )

      val request   = FakeRequest(GET, "/personal-account/self-assessment-summary")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result    = route(app, request).get
      val content   = Jsoup.parse(contentAsString(result))
      val niContent = content.getElementById("self-assessment-forms").text()
      niContent mustBe dummyContent
    }
  }

}
