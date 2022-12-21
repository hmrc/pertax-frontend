package views

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Application
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => getStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.scalatestaccessibilitylinter.domain.OutputFormat

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class testSpec extends IntegrationSpec {


  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled" -> true,
      "feature.breathing-space-indicator.timeoutInSec" -> 4
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

  "personal-account" must {
    "pass accessibility validation" in {

      server.stubFor(put(urlMatching(s"/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString)))
      server.stubFor(
        get(urlPathEqualTo(breathingSpaceUrl))
          .willReturn(ok(breathingSpaceTrueResponse))
      )

      val result: Future[Result] = route(app, request).get
      getStatus(result) mustBe OK
      contentAsString(result) must passAccessibilityChecks(OutputFormat.Verbose)
    }
  }
}
