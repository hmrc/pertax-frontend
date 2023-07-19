package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.admin.{AddressTaxCreditsBrokerCallToggle, PertaxBackendToggle, SingleAccountCheckToggle}
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.Assertion
import play.api.Application
import play.api.http.Status._
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.{SessionId, SessionKeys}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class TaxCreditsChoiceControllerItSpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port"                   -> server.port(),
      "microservice.services.citizen-details.port"        -> server.port(),
      "microservice.services.tcs-broker.port"             -> server.port(),
      "microservice.services.cachable.session-cache.port" -> server.port(),
      "microservice.services.cachable.session-cache.host" -> "127.0.0.1",
      "cookie.encryption.key"                             -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "sso.encryption.key"                                -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "queryParameter.encryption.key"                     -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "json.encryption.key"                               -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "metrics.enabled"                                   -> false
    )
    .build()

  val sessionId: Option[SessionId]                                  = Some(SessionId("session-00000000-0000-0000-0000-000000000000"))
  lazy val addressJourneyCachingHelper: AddressJourneyCachingHelper =
    app.injector.instanceOf[AddressJourneyCachingHelper]

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PertaxBackendToggle)))
      .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, false)))
  }

  def assertContainsLink(doc: Document, text: String, href: String): Assertion =
    assert(
      doc.getElementsContainingText(text).attr("href").contains(href),
      s"\n\nLink $href was not rendered on the page\n"
    )

  "/personal-account/your-address/tax-credits-choice" must {
    val url = "/personal-account/your-address/tax-credits-choice"

    val tcsBrokerUrl = s"/tcs/$generatedNino/dashboard-data"

    val citizenDetailsUrl = s"/citizen-details/nino/$generatedNino"

    val personDetailsUrl = s"/citizen-details/$generatedNino/designatory-details"

    val cacheMap = s"/keystore/pertax-frontend"

    "return a SEE_OTHER and redirect to the TCS Address change interstitial page if the user is a TCS user" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, true)))

      server.stubFor(
        get(urlEqualTo(citizenDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/citizen-details.json")))
      )

      server.stubFor(
        get(urlEqualTo(personDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
      )

      server.stubFor(
        get(urlPathMatching(s"$cacheMap/.*"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody("""
                                                            |{
                                                            |	"id": "session-id",
                                                            |	"data": {
                                                            |   "addressPageVisitedDto": {
                                                            |     "hasVisitedPage": true
                                                            |   }
                                                            |	},
                                                            |	"modifiedDetails": {
                                                            |		"createdAt": {
                                                            |			"$date": 1400258561678
                                                            |		},
                                                            |		"lastUpdated": {
                                                            |			"$date": 1400258561675
                                                            |		}
                                                            |	}
                                                            |}
                                                          |""".stripMargin)
          )
      )

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/dashboard-data.json")))
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe SEE_OTHER
      result.get.futureValue.header.headers.get("Location") mustBe Some(
        "/personal-account/your-address/change-address-tax-credits"
      )
    }

    "Show the tax credits question when the AddressTaxCreditsBrokerCallToggle is false" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, false)))

      lazy val messagesApi                 = app.injector.instanceOf[MessagesApi]
      implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

      lazy val app: Application = localGuiceApplicationBuilder()
        .configure(
          "microservice.services.auth.port"                   -> server.port(),
          "microservice.services.citizen-details.port"        -> server.port(),
          "microservice.services.tcs-broker.port"             -> server.port(),
          "microservice.services.cachable.session-cache.port" -> server.port(),
          "microservice.services.cachable.session-cache.host" -> "127.0.0.1"
        )
        .configure(
          Map(
            "cookie.encryption.key"                               -> "gvBoGdgzqG1AarzF1LY0zQ==",
            "sso.encryption.key"                                  -> "gvBoGdgzqG1AarzF1LY0zQ==",
            "queryParameter.encryption.key"                       -> "gvBoGdgzqG1AarzF1LY0zQ==",
            "json.encryption.key"                                 -> "gvBoGdgzqG1AarzF1LY0zQ==",
            "metrics.enabled"                                     -> false,
            "feature.address-change-tax-credits-question.enabled" -> true
          )
        )
        .build()

      server.stubFor(
        get(urlEqualTo(citizenDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/citizen-details.json")))
      )

      server.stubFor(
        get(urlEqualTo(personDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
      )

      server.stubFor(
        get(urlPathMatching(s"$cacheMap/.*"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody("""
                                                            |{
                                                            |	"id": "session-id",
                                                            |	"data": {
                                                            |   "addressPageVisitedDto": {
                                                            |     "hasVisitedPage": true
                                                            |   }
                                                            |	},
                                                            |	"modifiedDetails": {
                                                            |		"createdAt": {
                                                            |			"$date": 1400258561678
                                                            |		},
                                                            |		"lastUpdated": {
                                                            |			"$date": 1400258561675
                                                            |		}
                                                            |	}
                                                            |}
                                                            |""".stripMargin)
          )
      )

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/dashboard-data.json")))
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe OK
      contentAsString(result.get) must include(messages("label.do_you_get_tax_credits"))
    }

    "return a SEE_OTHER and redirect to PTA Address Change if the user is a non-TCS user" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, true)))

      server.stubFor(
        get(urlEqualTo(citizenDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/citizen-details.json")))
      )

      server.stubFor(
        get(urlEqualTo(personDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
      )

      server.stubFor(
        get(urlPathMatching(s"$cacheMap/.*"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody("""
                                                            |{
                                                            |	"id": "session-id",
                                                            |	"data": {
                                                            |   "addressPageVisitedDto": {
                                                            |     "hasVisitedPage": true
                                                            |   }
                                                            |	},
                                                            |	"modifiedDetails": {
                                                            |		"createdAt": {
                                                            |			"$date": 1400258561678
                                                            |		},
                                                            |		"lastUpdated": {
                                                            |			"$date": 1400258561675
                                                            |		}
                                                            |	}
                                                            |}
                                                            |""".stripMargin)
          )
      )

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe SEE_OTHER

      result.get.futureValue.header.headers.get("Location") mustBe Some(
        "/personal-account/your-address/residential/do-you-live-in-the-uk"
      )
    }

    "return a SEE_OTHER and redirect to the beginning of the PTA address change journey if the user skipped to tax credits url" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, true)))

      server.stubFor(
        get(urlEqualTo(citizenDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/citizen-details.json")))
      )

      server.stubFor(
        get(urlEqualTo(personDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
      )

      server.stubFor(
        get(urlPathMatching(s"$cacheMap/.*"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody("""
                                                            |{
                                                            |	"id": "session-id",
                                                            |	"data": {
                                                            |   "addressPageVisitedDto": {
                                                            |     "hasVisitedPage": true
                                                            |   }
                                                            |	},
                                                            |	"modifiedDetails": {
                                                            |		"createdAt": {
                                                            |			"$date": 1400258561678
                                                            |		},
                                                            |		"lastUpdated": {
                                                            |			"$date": 1400258561675
                                                            |		}
                                                            |	}
                                                            |}
                                                            |""".stripMargin)
          )
      )

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe SEE_OTHER

      result.get.futureValue.header.headers.get("Location") mustBe Some(
        "/personal-account/your-address/residential/do-you-live-in-the-uk"
      )
    }

    List(
      BAD_REQUEST,
      IM_A_TEAPOT,
      INTERNAL_SERVER_ERROR,
      SERVICE_UNAVAILABLE
    ).foreach { response =>
      s"return an INTERNAL_SERVER_ERROR and redirect to PTA Address Change if the call to TCS fails with a $response" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, true)))

        server.stubFor(
          get(urlEqualTo(citizenDetailsUrl))
            .willReturn(ok(FileHelper.loadFile("./it/resources/citizen-details.json")))
        )

        server.stubFor(
          get(urlEqualTo(personDetailsUrl))
            .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
        )

        server.stubFor(
          get(urlPathMatching(s"$cacheMap/.*"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody("""
                                                              |{
                                                              |	"id": "session-id",
                                                              |	"data": {
                                                              |   "addressPageVisitedDto": {
                                                              |     "hasVisitedPage": true
                                                              |   }
                                                              |	},
                                                              |	"modifiedDetails": {
                                                              |		"createdAt": {
                                                              |			"$date": 1400258561678
                                                              |		},
                                                              |		"lastUpdated": {
                                                              |			"$date": 1400258561675
                                                              |		}
                                                              |	}
                                                              |}
                                                              |""".stripMargin)
            )
        )

        server.stubFor(
          get(urlEqualTo(tcsBrokerUrl))
            .willReturn(aResponse().withStatus(response))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
        val result  = route(app, request)

        result.get.futureValue.header.status mustBe INTERNAL_SERVER_ERROR

        result.get.futureValue.header.headers.get("Location") mustBe None
      }
    }
  }
}
