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

package controllers.auth

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, post, urlEqualTo, urlMatching, status => _}
import config.ConfigDecorator
import models.admin._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status => getStatus, _}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import views.html.InternalServerErrorView

import scala.concurrent.Future

class PertaxAuthActionItSpec extends IntegrationSpec {

  val url = "/personal-account"

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.pertax.port" -> server.port()
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PaperlessInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(PaperlessInterruptToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PertaxBackendToggle)))
      .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, isEnabled = true)))
  }

  "personal-account" must {
    "allow the user to progress to the service" when {
      "Pertax returns ACCESS_GRANTED" in {
        server.stubFor(
          get(urlEqualTo(s"/pertax/$generatedNino/authorise"))
            .willReturn(ok("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}"))
        )
        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
        val result  = route(app, request)
        result.map(getStatus).get mustBe OK
      }
      "Pertax toggle is off" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(PertaxBackendToggle)))
          .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, isEnabled = false)))

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
        val result  = route(app, request)
        result.map(getStatus).get mustBe OK
      }
    }
    "redirect" when {
      "Pertax returns NO_HMRC_PT_ENROLMENT" in {
        server.stubFor(
          get(urlEqualTo(s"/pertax/$generatedNino/authorise"))
            .willReturn(
              ok(
                "{\"code\": \"NO_HMRC_PT_ENROLMENT\", \"message\": \"No HMRC-PT enrolment\", \"redirect\": \"personal-account\"}"
              )
            )
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
        val result  = route(app, request)
        result.map(getStatus).get mustBe SEE_OTHER
        result.map(redirectLocation).get mustBe Some("personal-account/?redirectUrl=%2Fpersonal-account")
      }
    }
    "return INTERNAL_SERVER_ERROR" when {
      "Pertax returns an error view, without a valid HTML" in {
        val body =
          s"""
             |{
             |  "code": "INVALID_AFFINITY",
             |  "message": "The user is neither an individual or an organisation",
             |  "errorView": {
             |     "url": "/pertax/personal-account",
             |     "statusCode": 401
             |  }
             |}
             |""".stripMargin

        server.stubFor(
          get(urlEqualTo(s"/pertax/$generatedNino/authorise"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  body
                )
            )
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
        val result  = route(app, request)
        result.map(getStatus).get mustBe INTERNAL_SERVER_ERROR
      }
    }
    "return an error view with the status retrieved from the backend" when {
      List(
        BAD_REQUEST,
        UNAUTHORIZED,
        NOT_FOUND,
        NOT_IMPLEMENTED,
        INTERNAL_SERVER_ERROR,
        BAD_GATEWAY
      ).foreach { errorCode =>
        s"Pertax returns an error view with status $errorCode, with a valid HTML" in {

          val body =
            s"""
               |{
               |  "code": "INVALID_AFFINITY",
               |  "message": "The user is neither an individual or an organisation",
               |  "errorView": {
               |     "url": "/pertax/personal-account",
               |     "statusCode": $errorCode
               |  }
               |}
               |""".stripMargin

          val configDecorator                  = app.injector.instanceOf[ConfigDecorator]
          val messagesApi                      = app.injector.instanceOf[MessagesApi]
          implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)
          val view                             = app.injector
            .instanceOf[InternalServerErrorView]
            .render(
              FakeRequest(),
              configDecorator,
              messages
            )

          server.stubFor(
            get(urlEqualTo(s"/pertax/$generatedNino/authorise"))
              .willReturn(
                aResponse()
                  .withStatus(OK)
                  .withBody(
                    body
                  )
              )
          )

          server.stubFor(
            get(urlEqualTo(s"/pertax/personal-account"))
              .willReturn(
                aResponse()
                  .withStatus(OK)
                  .withBody(
                    contentAsString(view)
                  )
              )
          )

          val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
          val result  = route(app, request)
          result.map(getStatus).get mustBe errorCode
        }
      }
    }
  }
}
