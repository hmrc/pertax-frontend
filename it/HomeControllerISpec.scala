import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Application
import play.api.http.Status.TOO_MANY_REQUESTS
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_GATEWAY, BAD_REQUEST, GET, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, NOT_FOUND, SERVICE_UNAVAILABLE, UNPROCESSABLE_ENTITY, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class HomeControllerISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"      -> true,
      "feature.breathing-space-indicator.timeoutInSec" -> 4
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

  "personal-account" must {
    "show BreathingSpaceIndicator when receive true response from BreathingSpaceIfProxy" in {

      server.stubFor(
        put(urlMatching(s"/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )
      server.stubFor(
        get(urlPathEqualTo(breathingSpaceUrl))
          .willReturn(ok(breathingSpaceTrueResponse))
      )

      val result: Future[Result] = route(app, request).get
      contentAsString(result).contains("BREATHING SPACE") mustBe true
      contentAsString(result).contains("/personal-account/breathing-space") mustBe true
      server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
    }

    "hide BreathingSpaceIndicator when receive false response from BreathingSpaceIfProxy" in {

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
          put(urlMatching(s"/keystore/pertax-frontend/.*"))
            .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
        )
        server.stubFor(
          get(urlPathEqualTo(breathingSpaceUrl))
            .willReturn(aResponse.withStatus(httpResponse))
        )

        val result: Future[Result] = route(app, request).get
        contentAsString(result).contains("BREATHING SPACE") mustBe false
        contentAsString(result).contains("/personal-account/breathing-space") mustBe false
        server.verify(1, getRequestedFor(urlEqualTo(s"/$generatedNino/memorandum")))
      }
    }

  }

}
