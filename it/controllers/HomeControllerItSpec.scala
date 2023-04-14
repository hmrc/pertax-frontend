package controllers

import play.api.Application
import play.api.http.Status.OK
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys

import java.util.UUID

class HomeControllerItSpec extends IntegrationSpec {
  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.preferences-frontend.port" -> server.port()
    )
    .build()

  val uuid: String = UUID.randomUUID().toString

  override def beforeEach(): Unit =
    super.beforeEach()

  "/personal-account" when {
    val url = "/personal-accountvbxdfgb"

    "a " must {
      "b " in {

        val request = FakeRequest(GET, url)
          .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
        val result  = route(app, request)

        status(result.get) mustBe OK
      }
    }

  }

}
