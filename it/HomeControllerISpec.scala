import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{SingleAccountCheckToggle, TaxcalcToggle}
import play.api.Application
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import services.admin.FeatureFlagService
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.domain.{Generator, SaUtr}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class HomeControllerISpec extends IntegrationSpec {

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

  val url = s"/personal-account"

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  val generatedUtr = new Generator().nextAtedUtr.utr

  implicit lazy val ec = app.injector.instanceOf[ExecutionContext]

  val breathingSpaceUrl = s"/$generatedNino/memorandum"

  val breathingSpaceTrueResponse =
    s"""
       |{
       |    "breathingSpaceIndicator": true
       |}
       |""".stripMargin

  val breathingSpaceFalseResponse =
    s"""
       |{
       |    "breathingSpaceIndicator": false
       |}
       |""".stripMargin

  val uuid: String = UUID.randomUUID().toString

  override def beforeEach() = {
    server.resetAll()
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(aResponse().withStatus(404))
    )
    server.stubFor(
      get(urlMatching("/keystore/pertax-frontend/.*"))
        .willReturn(aResponse().withStatus(404))
    )
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))

    lazy val featureFlagService = app.injector.instanceOf[FeatureFlagService]
    featureFlagService.set(TaxcalcToggle, false).futureValue
    featureFlagService.set(SingleAccountCheckToggle, true).futureValue
  }

  "personal-account" must {
    "show BreathingSpaceIndicator when receive true response from BreathingSpaceIfProxy" in {
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
      server.stubFor(get(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")).willReturn(serverError()))
      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(serverError())
      )
      server.stubFor(
        put(urlMatching("/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )
      server.stubFor(
        get(urlPathEqualTo(breathingSpaceUrl))
          .willReturn(ok(breathingSpaceTrueResponse))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains("BREATHING SPACE") mustBe true
      contentAsString(result).contains("/personal-account/breathing-space") mustBe true
      server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(0, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))

      val urlBreathingSpace = "/personal-account/breathing-space"

      val requestBreathingSpace = FakeRequest(GET, urlBreathingSpace)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultBreathingSpace  = route(app, requestBreathingSpace)

      Helpers.status(resultBreathingSpace.get) mustBe OK
      contentAsString(resultBreathingSpace.get) must include("You are in Breathing Space")
      contentAsString(resultBreathingSpace.get) must include(
        "The Debt Respite Scheme (Breathing Space) gives people in debt the right to legal protections from their creditors."
      )
    }

    "hide BreathingSpaceIndicator when receive false response from BreathingSpaceIfProxy" in {
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(serverError())
      )
      server.stubFor(
        put(urlMatching(s"/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )
      server.stubFor(
        get(urlPathEqualTo(breathingSpaceUrl))
          .willReturn(ok(breathingSpaceFalseResponse))
      )

      val result: Future[Result] = route(app, request).get
      contentAsString(result).contains("BREATHING SPACE") mustBe false
      contentAsString(result).contains("/personal-account/breathing-space") mustBe false
      server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(0, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
    }

    "show correct message on the Marriage Allowance tile when transferring Personal Allowance to partner" in {

      val taxComponentsJson = Json
        .parse("""{
          |   "data" : [ {
          |      "componentType" : "MarriageAllowanceTransferred",
          |      "employmentId" : 12,
          |      "amount" : 12321,
          |      "inputAmount" : 12321,
          |      "description" : "Personal Allowance transferred to partner",
          |      "iabdCategory" : "Deduction"
          |   } ],
          |   "links" : [ ]
          |}""".stripMargin)
        .toString

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(ok(taxComponentsJson))
      )
      server.stubFor(
        put(urlMatching("/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK

      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
      )

      contentAsString(result).contains(
        "You currently transfer part of your Personal Allowance to your partner."
      ) mustBe true
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
      )
    }

    "show correct message on the Marriage Allowance tile when receiving Personal Allowance from partner" in {

      val taxComponentsJson = Json
        .parse("""{
            |   "data" : [ {
            |      "componentType" : "MarriageAllowanceReceived",
            |      "employmentId" : 12,
            |      "amount" : 12321,
            |      "inputAmount" : 12321,
            |      "description" : "Personal Allowance transferred to partner",
            |      "iabdCategory" : "Deduction"
            |   } ],
            |   "links" : [ ]
            |}""".stripMargin)
        .toString

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(ok(taxComponentsJson))
      )
      server.stubFor(
        put(urlMatching("/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK

      contentAsString(result).contains(
        "Your partner currently transfers part of their Personal Allowance to you."
      ) mustBe true
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
      )
    }

    "show correct message on the Marriage Allowance tile when not transferring or receiving Personal Allowance from partner" in {

      val taxComponentsJson = Json
        .parse("""{
            |   "data" : [ {
            |      "componentType" : "OtherAllowance",
            |      "employmentId" : 12,
            |      "amount" : 12321,
            |      "inputAmount" : 12321,
            |      "description" : "Personal Allowance transferred to partner",
            |      "iabdCategory" : "Deduction"
            |   } ],
            |   "links" : [ ]
            |}""".stripMargin)
        .toString

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(ok(taxComponentsJson))
      )
      server.stubFor(
        put(urlMatching("/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK

      contentAsString(result).contains(
        "Transfer part of your Personal Allowance to your partner so they pay less tax."
      ) mustBe true
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
      )
    }

    "Return LOCKED status and display the MCI error page when designatory-details returns LOCKED" in {

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(aResponse().withStatus(LOCKED))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe LOCKED

      contentAsString(result).contains(
        "We cannot access your details"
      ) mustBe true
    }

    "show SaUtr when user has an SaUtr in the auth body" in {
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
           |       },
           |       "key":"IR-SA",
           |          "identifiers": [
           |             {
           |                "key":"UTR",
           |                "value": "$generatedUtr"
           |             }
           |          ]
           |       },
           |    ],
           |    "affinityGroup": "Individual",
           |    "credentialStrength": "strong"
           |}
           |""".stripMargin

      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
      server.stubFor(get(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")).willReturn(serverError()))
      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(serverError())
      )
      server.stubFor(
        put(urlMatching("/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(generatedUtr) mustBe true

    }

//    List(
//      TOO_MANY_REQUESTS,
//      INTERNAL_SERVER_ERROR,
//      BAD_GATEWAY,
//      SERVICE_UNAVAILABLE,
//      IM_A_TEAPOT,
//      NOT_FOUND,
//      BAD_REQUEST,
//      UNPROCESSABLE_ENTITY
//    ).foreach { httpResponse =>
//      s"return a $httpResponse when $httpResponse status is received" in {
//        server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
//        server.stubFor(
//          get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
//            .willReturn(serverError())
//        )
//        server.stubFor(
//          put(urlMatching(s"/keystore/pertax-frontend/.*"))
//            .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
//        )
//        server.stubFor(
//          get(urlPathEqualTo(breathingSpaceUrl))
//            .willReturn(aResponse.withStatus(httpResponse))
//        )
//
//        val result: Future[Result] = route(app, request).get
//        contentAsString(result).contains("BREATHING SPACE") mustBe false
//        contentAsString(result).contains("/personal-account/breathing-space") mustBe false
//        server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
//        server.verify(0, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
//      }
//    }
//
//    "redirect to protect-tax-info if HMRC-PT enrolment is not present" in {
//      val authResponseNoHmrcPt =
//        s"""
//           |{
//           |    "confidenceLevel": 200,
//           |    "nino": "$generatedNino",
//           |    "name": {
//           |        "name": "John",
//           |        "lastName": "Smith"
//           |    },
//           |    "loginTimes": {
//           |        "currentLogin": "2021-06-07T10:52:02.594Z",
//           |        "previousLogin": null
//           |    },
//           |    "optionalCredentials": {
//           |        "providerId": "4911434741952698",
//           |        "providerType": "GovernmentGateway"
//           |    },
//           |    "authProviderId": {
//           |        "ggCredId": "xyz"
//           |    },
//           |    "externalId": "testExternalId",
//           |    "allEnrolments": [],
//           |    "affinityGroup": "Individual",
//           |    "credentialStrength": "strong"
//           |}
//           |""".stripMargin
//
//      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponseNoHmrcPt)))
//      server.stubFor(
//        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
//          .willReturn(serverError())
//      )
//      server.stubFor(
//        put(urlMatching(s"/keystore/pertax-frontend/.*"))
//          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
//      )
//      server.stubFor(
//        get(urlPathEqualTo(breathingSpaceUrl))
//          .willReturn(ok(breathingSpaceTrueResponse))
//      )
//
//      val result: Future[Result] = route(app, request).get
//      httpStatus(result) mustBe SEE_OTHER
//      redirectLocation(result) mustBe Some("http://localhost:7750/protect-tax-info?redirectUrl=%2Fpersonal-account")
//      server.verify(0, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
//      server.verify(0, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
//    }

  }
}
