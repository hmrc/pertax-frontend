package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{SingleAccountCheckToggle, TaxComponentsToggle, TaxcalcToggle}
import play.api.Application
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import services.admin.FeatureFlagService
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class HomeControllerSelfAssessmentISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"      -> true,
      "feature.breathing-space-indicator.timeoutInSec" -> 4,
      "microservice.services.taxcalc.port"             -> server.port(),
      "microservice.services.tai.port"                 -> server.port(),
      "feature.business-hours.Monday.start-time"       -> "0:00",
      "feature.business-hours.Monday.end-time"         -> "23:59",
      "feature.business-hours.Tuesday.start-time"      -> "0:00",
      "feature.business-hours.Tuesday.end-time"        -> "23:59",
      "feature.business-hours.Wednesday.start-time"    -> "0:00",
      "feature.business-hours.Wednesday.end-time"      -> "23:59",
      "feature.business-hours.Thursday.start-time"     -> "0:00",
      "feature.business-hours.Thursday.end-time"       -> "23:59",
      "feature.business-hours.Friday.start-time"       -> "0:00",
      "feature.business-hours.Friday.end-time"         -> "23:59",
      "feature.business-hours.Saturday.start-time"     -> "0:00",
      "feature.business-hours.Saturday.end-time"       -> "23:59",
      "feature.business-hours.Sunday.start-time"       -> "0:00",
      "feature.business-hours.sunday.end-time"         -> "23:59"
    )
    .build()

  val url          = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  implicit lazy val ec: ExecutionContext           = app.injector.instanceOf[ExecutionContext]

  override def beforeEach(): Unit = {
    server.resetAll()
    server.stubFor(
      get(urlMatching("/keystore/pertax-frontend/.*"))
        .willReturn(aResponse().withStatus(NOT_FOUND))
    )
    server.stubFor(
      put(urlMatching("/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
    )
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(aResponse().withStatus(NOT_FOUND))
    )
    server.stubFor(get(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")).willReturn(serverError()))
    server.stubFor(
      get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
        .willReturn(serverError())
    )
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
    server.stubFor(get(urlMatching(s"/$generatedNino/memorandum")).willReturn(serverError()))

    lazy val featureFlagService = app.injector.instanceOf[FeatureFlagService]
    featureFlagService.set(TaxcalcToggle, enabled = false).futureValue
    featureFlagService.set(SingleAccountCheckToggle, enabled = true).futureValue
    featureFlagService.set(TaxComponentsToggle, enabled = true).futureValue
  }

  "personal-account" must {
    "show SaUtr and Request Access message when user has an SaUtr in the matching details body but not the auth body" in {

      val citizenResponse =
        s"""|
           |{
            |  "name": {
            |    "current": {
            |      "firstName": "John",
            |      "lastName": "Smith"
            |    },
            |    "previous": []
            |  },
            |  "ids": {
            |    "nino": "$generatedNino",
            |    "sautr": "$generatedUtr"
            |  },
            |  "dateOfBirth": "11121971"
            |}
            |""".stripMargin

      val authResponse =
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
           |       }
           |    ],
           |    "affinityGroup": "Individual",
           |    "credentialStrength": "strong"
           |}
           |""".stripMargin

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(generatedUtr) mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains("Request access to your Self Assessment") mustBe true
    }

    "show SaUtr and Activate your Self Assessment message when user has an SaUtr in the auth body which has the NotYetActivated state" in {

      val authResponse =
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
           |            "key":"IR-SA",
           |            "identifiers": [
           |                {
           |                    "key":"UTR",
           |                    "value": "$generatedUtr"
           |                }
           |            ],
           |            "state": "NotYetActivated"
           |        }
           |    ],
           |    "affinityGroup": "Individual",
           |    "credentialStrength": "strong"
           |}
           |""".stripMargin

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(generatedUtr) mustBe true
      contentAsString(result).contains("Self Assessment") mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains("Activate your Self Assessment") mustBe true
    }

    "show SaUtr and Complete your tax return message when user has an SaUtr in the auth body which has the Activated state" in {

      val authResponse =
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
           |            "key":"IR-SA",
           |            "identifiers": [
           |                {
           |                    "key":"UTR",
           |                    "value": "$generatedUtr"
           |                }
           |            ],
           |            "state": "Activated"
           |        }
           |    ],
           |    "affinityGroup": "Individual",
           |    "credentialStrength": "strong"
           |}
           |""".stripMargin

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(generatedUtr) mustBe true
      contentAsString(result).contains("Self Assessment") mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains("Complete your tax return") mustBe true
      contentAsString(resultSa).contains("View your payments") mustBe true
    }

    "not show SaUtr or Self Assessment tile if no SaUtr is present in the auth or citizen details body" in {

      val authResponse =
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
           |       }
           |    ],
           |    "affinityGroup": "Individual",
           |    "credentialStrength": "strong"
           |}
           |""".stripMargin

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains("Self Assessment") mustBe false

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe UNAUTHORIZED
    }
  }
}
