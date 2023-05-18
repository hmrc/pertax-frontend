package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{SingleAccountCheckToggle, TaxcalcToggle}
import play.api.Application
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import play.api.test.{FakeRequest, Helpers}
import services.admin.FeatureFlagService
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys

import java.util.UUID
import scala.concurrent.Future

class HomeControllerBreathingSpaceISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"      -> true,
      "feature.breathing-space-indicator.timeoutInSec" -> 4,
      "microservice.services.taxcalc.port"             -> server.port(),
      "microservice.services.tai.port"                 -> server.port()
    )
    .build()

  val url          = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  val breathingSpaceUrl                            = s"/$generatedNino/memorandum"

  val breathingSpaceTrueResponse: String =
    s"""
       |{
       |    "breathingSpaceIndicator": true
       |}
       |""".stripMargin

  val breathingSpaceFalseResponse: String =
    s"""
       |{
       |    "breathingSpaceIndicator": false
       |}
       |""".stripMargin

  override def beforeEach(): Unit = {
    server.resetAll()
    beforeEachHomeController(memorandum = false)

    lazy val featureFlagService = app.injector.instanceOf[FeatureFlagService]
    featureFlagService.set(TaxcalcToggle, enabled = false).futureValue
    featureFlagService.set(SingleAccountCheckToggle, enabled = true).futureValue
  }

  "personal-account" must {
    val urlBreathingSpace = controllers.routes.InterstitialController.displayBreathingSpaceDetails.url

    "show BreathingSpaceIndicator when receive true response from BreathingSpaceIfProxy" in {
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
        server.stubFor(
          get(urlPathEqualTo(breathingSpaceUrl))
            .willReturn(aResponse.withStatus(httpResponse))
        )

        val result: Future[Result] = route(app, request).get
        contentAsString(result).contains(Messages("label.breathing_space")) mustBe false
        contentAsString(result).contains(urlBreathingSpace) mustBe false
        server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
        server.verify(0, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
      }
    }
  }
}
