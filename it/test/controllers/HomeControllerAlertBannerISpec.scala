/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        .willReturn(
          ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino))
        )
    )
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerPaperlessStatusToggle)))
      .thenReturn(Future.successful(FeatureFlag(AlertBannerPaperlessStatusToggle, true)))
  }

  "personal-account" must {
    "show alert banner" when {
      "paperless status is BOUNCED_EMAIL" in {
        val link = "http://some/link"
        server.stubFor(get(urlMatching("/paperless/status.*")).willReturn(ok(s"""{
                                                                                |  "status": {
                                                                                |    "name": "BOUNCED_EMAIL",
                                                                                |    "category": "INFO",
                                                                                |    "text": "Unused"
                                                                                |  },
                                                                                |  "url": {
                                                                                |    "link": "$link",
                                                                                |    "text": "Unused"
                                                                                |  }
                                                                                |}""".stripMargin)))

        val result: Future[Result] = route(app, request).get
        val html                   = Jsoup.parse(contentAsString(result))
        httpStatus(result) mustBe OK
        val banner                 = html.getElementById("alert-banner")
        banner.toString must include("We are having trouble sending you emails")
        banner.toString must include("check your email address")
        banner.toString must include(link)
      }

      "paperless status is EMAIL_NOT_VERIFIED" in {
        val link = "http://some/link"
        server.stubFor(get(urlMatching("/paperless/status.*")).willReturn(ok(s"""{
                                                                                |  "status": {
                                                                                |    "name": "EMAIL_NOT_VERIFIED",
                                                                                |    "category": "INFO",
                                                                                |    "text": "Unused"
                                                                                |  },
                                                                                |  "url": {
                                                                                |    "link": "$link",
                                                                                |    "text": "Unused"
                                                                                |  }
                                                                                |}""".stripMargin)))

        val result: Future[Result] = route(app, request).get
        val html                   = Jsoup.parse(contentAsString(result))
        httpStatus(result) mustBe OK
        val banner                 = html.getElementById("alert-banner")
        banner.toString must include("verify your email address")
        banner.toString must include(link)
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
