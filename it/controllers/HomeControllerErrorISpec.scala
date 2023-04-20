package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{SingleAccountCheckToggle, TaxcalcToggle}
import play.api.Application
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, defaultAwaitTimeout, redirectLocation, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import services.admin.FeatureFlagService
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class HomeControllerErrorISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"      -> true,
      "feature.breathing-space-indicator.timeoutInSec" -> 4,
      "microservice.services.taxcalc.port"             -> server.port(),
      "microservice.services.tai.port"                 -> server.port()
    )
    .build()

  val url = s"/personal-account"

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  override def beforeEach(): Unit = {
    server.resetAll()
    server.stubFor(
      get(urlMatching("/keystore/pertax-frontend/.*"))
        .willReturn(aResponse().withStatus(NOT_FOUND))
    )
    server.stubFor(
      put(urlMatching(s"/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
    )
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(aResponse().withStatus(NOT_FOUND))
    )
    server.stubFor(
      get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
        .willReturn(serverError())
    )
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))

    lazy val featureFlagService = app.injector.instanceOf[FeatureFlagService]
    featureFlagService.set(TaxcalcToggle, enabled = false).futureValue
    featureFlagService.set(SingleAccountCheckToggle, enabled = true).futureValue
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

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("http://localhost:7750/protect-tax-info?redirectUrl=%2Fpersonal-account")
      server.verify(0, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      server.verify(0, getRequestedFor(urlEqualTo(s"/taxcalc/$generatedNino/reconciliations")))
    }
  }
}
