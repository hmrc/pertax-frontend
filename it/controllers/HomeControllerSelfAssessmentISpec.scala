package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{SingleAccountCheckToggle, TaxComponentsToggle}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.util.UUID
import scala.concurrent.Future

class HomeControllerSelfAssessmentISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"        -> true,
      "feature.breathing-space-indicator.timeoutInSec"   -> 4,
      "microservice.services.taxcalc.port"               -> server.port(),
      "microservice.services.tai.port"                   -> server.port(),
      "microservice.services.enrolment-store-proxy.port" -> server.port()
    )
    .build()

  val url          = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController(auth = false, matchingDetails = false)
    server.stubFor(
      put(urlMatching("/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
    )
    server.stubFor(
      get(urlMatching(s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$generatedUtr/users"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
    )

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, true)))
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
           |    "sautr": "$generatedUtr",
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
      contentAsString(result).contains(Messages("label.home_page.utr")) mustBe true
      contentAsString(result).contains(generatedUtr) mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains(Messages("label.not_enrolled.link.text")) mustBe true
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

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(generatedUtr) mustBe true
      contentAsString(result).contains(Messages("label.self_assessment")) mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains(Messages("label.activate_your_self_assessment")) mustBe true
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

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(generatedUtr) mustBe true
      contentAsString(result).contains(Messages("label.self_assessment")) mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains(Messages("label.complete_your_tax_return")) mustBe true
      contentAsString(resultSa).contains(Messages("label.view_your_payments")) mustBe true
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

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(Messages("label.self_assessment")) mustBe false

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe UNAUTHORIZED
    }
  }
}
