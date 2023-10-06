package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{AlertBannerPaperlessStatusToggle}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.util.UUID
import scala.concurrent.Future

class HomeControllerAlertBannerISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"       -> false,
      "microservice.services.preferences-frontend.port" -> server.port()
    )
    .build()

  val url = s"/personal-account"

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()

    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
    )
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerPaperlessStatusToggle)))
      .thenReturn(Future.successful(FeatureFlag(AlertBannerPaperlessStatusToggle, true)))
  }

  "personal-account" must {
    "show alert banner" when {
      "paperless status is BOUNCED_EMAIL" in {
        server.stubFor(get(urlMatching("/paperless/status.*")).willReturn(ok("""{
             |  "status": {
             |    "name": "BOUNCED_EMAIL",
             |    "category": "INFO",
             |    "text": "Unused"
             |  },
             |  "url": {
             |    "link": "http://some/unused/link",
             |    "text": "Unused"
             |  }
             |}""".stripMargin)))

        val result: Future[Result] = route(app, request).get
        val html                   = Jsoup.parse(contentAsString(result))
        httpStatus(result) mustBe OK
        val banner                 = html.getElementById("alert-banner")
        banner.toString must include("We are having trouble sending you emails")
        banner.toString must include("check your email address")
        banner.toString must include("/paperless/email-bounce?")
      }

      "paperless status is EMAIL_NOT_VERIFIED" in {
        server.stubFor(get(urlMatching("/paperless/status.*")).willReturn(ok("""{
                                                                               |  "status": {
                                                                               |    "name": "EMAIL_NOT_VERIFIED",
                                                                               |    "category": "INFO",
                                                                               |    "text": "Unused"
                                                                               |  },
                                                                               |  "url": {
                                                                               |    "link": "http://some/unused/link",
                                                                               |    "text": "Unused"
                                                                               |  }
                                                                               |}""".stripMargin)))

        val result: Future[Result] = route(app, request).get
        val html                   = Jsoup.parse(contentAsString(result))
        httpStatus(result) mustBe OK
        val banner                 = html.getElementById("alert-banner")
        banner.toString must include("verify your email address")
        banner.toString must include("/paperless/email-re-verify?")
      }
    }
    "not show alert banner" when {
      "paperless status is ALRIGHT" in {
        server.stubFor(get(urlMatching("/paperless/status.*")).willReturn(ok("""{
                                                                               |  "status": {
                                                                               |    "name": "ALRIGHT",
                                                                               |    "category": "INFO",
                                                                               |    "text": "Unused"
                                                                               |  },
                                                                               |  "url": {
                                                                               |    "link": "http://some/unused/link",
                                                                               |    "text": "Unused"
                                                                               |  }
                                                                               |}""".stripMargin)))

        val result: Future[Result] = route(app, request).get
        val html                   = Jsoup.parse(contentAsString(result))
        httpStatus(result) mustBe OK
        val banner                 = html.getElementById("alert-banner")
        banner mustBe null
      }
    }
  }
}
