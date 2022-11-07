import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Application
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import testUtils.IntegrationSpec
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
      "feature.single-account-enrolment.enabled"       -> true,
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
      "feature.business-hours.sunday.end-time"         -> "23:59",
      "microservice.services.pertax.port"              -> server.port()
    )
    .build()

  val url = s"/personal-account"

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid)
  }

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

  override def beforeEach() = {
    server.resetAll()
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
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
        put(urlMatching(s"/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )
      server.stubFor(
        get(urlPathEqualTo(breathingSpaceUrl))
          .willReturn(ok(breathingSpaceTrueResponse))
      )

      server.stubFor(
        get(urlEqualTo(s"/pertax/$generatedNino/authorise"))
          .willReturn(ok("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}"))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains("BREATHING SPACE") mustBe true
      contentAsString(result).contains("/personal-account/breathing-space") mustBe true
      server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(1, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
    }

    "hide BreathingSpaceIndicator when receive false response from BreathingSpaceIfProxy" in {
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
      server.stubFor(get(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")).willReturn(serverError()))
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
      server.stubFor(
        get(urlEqualTo(s"/pertax/$generatedNino/authorise"))
          .willReturn(ok("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}"))
      )

      val result: Future[Result] = route(app, request).get
      contentAsString(result).contains("BREATHING SPACE") mustBe false
      contentAsString(result).contains("/personal-account/breathing-space") mustBe false
      server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(1, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
    }

    List(
      TOO_MANY_REQUESTS,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE,
      IM_A_TEAPOT,
      NOT_FOUND,
      BAD_REQUEST,
      UNPROCESSABLE_ENTITY
    ).foreach { httpResponse =>
      s"return a $httpResponse when $httpResponse status is received" in {
        server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
        server.stubFor(get(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")).willReturn(serverError()))
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
            .willReturn(aResponse.withStatus(httpResponse))
        )

        server.stubFor(
          get(urlEqualTo(s"/pertax/$generatedNino/authorise"))
            .willReturn(ok("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}"))
        )

        val result: Future[Result] = route(app, request).get
        contentAsString(result).contains("BREATHING SPACE") mustBe false
        contentAsString(result).contains("/personal-account/breathing-space") mustBe false
        server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
        server.verify(1, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
      }
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
      server.stubFor(get(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")).willReturn(serverError()))
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
          .willReturn(ok(breathingSpaceTrueResponse))
      )
      server.stubFor(
        get(urlEqualTo(s"/pertax/$generatedNino/authorise"))
          .willReturn(
            ok(
              "{\"code\": \"NO_HMRC_PT_ENROLMENT\", \"message\": \"Access granted\", \"redirect\": \"http://localhost:7750/protect-tax-info\"}"
            )
          )
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("http://localhost:7750/protect-tax-info/?redirectUrl=%2Fpersonal-account")
      server.verify(0, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(0, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
    }

  }
}
