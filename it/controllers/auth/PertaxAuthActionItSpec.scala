package controllers.auth

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, post, urlEqualTo, urlMatching, status => _}
import config.ConfigDecorator
import models.admin._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status => getStatus, _}
import play.api.Application
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import views.html.InternalServerErrorView

import scala.concurrent.Future

class PertaxAuthActionItSpec extends IntegrationSpec {

  val url = "/personal-account"

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.pertax.port" -> server.port()
    )
    .build()

  override def beforeEach() = {
    super.beforeEach()
    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PaperlessInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(PaperlessInterruptToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcMakePaymentLinkToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcMakePaymentLinkToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PertaxBackendToggle)))
      .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, true)))
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
          .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, false)))

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
