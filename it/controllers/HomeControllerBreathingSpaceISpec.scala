package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{SingleAccountCheckToggle, TaxcalcToggle}
import play.api.Application
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import play.api.test.{FakeRequest, Helpers}
import services.admin.FeatureFlagService
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class HomeControllerBreathingSpaceISpec extends IntegrationSpec {

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
    val urlBreathingSpace = controllers.routes.InterstitialController.displayBreathingSpaceDetails.url

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
      contentAsString(result).contains(Messages("label.breathing_space")) mustBe true
      contentAsString(result).contains(urlBreathingSpace) mustBe true
      server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(0, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))

      val requestBreathingSpace = FakeRequest(GET, urlBreathingSpace)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultBreathingSpace  = route(app, requestBreathingSpace)

      Helpers.status(resultBreathingSpace.get) mustBe OK
      contentAsString(resultBreathingSpace.get) must include(Messages("label.you_are_in_breathing_space"))
      contentAsString(resultBreathingSpace.get) must include(
        Messages(
          "label.the_debt_respite_scheme_gives_people_in_debt_the_right_to_legal_protections_from_their_creditors"
        )
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
      contentAsString(result).contains(Messages("label.breathing_space")) mustBe false
      contentAsString(result).contains(urlBreathingSpace) mustBe false
      server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(0, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
    }
  }
}
