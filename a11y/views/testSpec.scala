package views

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.scalatestaccessibilitylinter.AccessibilityMatchers
import com.github.tomakehurst.wiremock.client.WireMock._
import models.AgentClientStatus
import play.api.Application
import play.api.http.Status.{OK, TOO_MANY_REQUESTS}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_GATEWAY, BAD_REQUEST, GET, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, NOT_FOUND, SERVICE_UNAVAILABLE, UNPROCESSABLE_ENTITY, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => getStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class testSpec extends IntegrationSpec with AccessibilityMatchers {


  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled" -> true,
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

      server.stubFor(put(urlMatching(s"/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString)))
      server.stubFor(
        get(urlPathEqualTo(breathingSpaceUrl))
          .willReturn(ok(breathingSpaceTrueResponse))
      )

      val result: Future[Result] = route(app, request).get
      contentAsString(result) must passAccessibilityChecks
    }
  }
}
