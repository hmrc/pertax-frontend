package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.admin.AddressTaxCreditsBrokerCallToggle
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.{Assertion, BeforeAndAfterEach}
import play.api.Application
import play.api.http.Status._
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.{SessionId, SessionKeys}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class TaxCreditsChoiceControllerItSpec extends IntegrationSpec with BeforeAndAfterEach {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port"                        -> server.port(),
      "microservice.services.citizen-details.port"             -> server.port(),
      "microservice.services.tcs-broker.port"                  -> server.port(),
      "microservice.services.tcs-broker.timeoutInMilliseconds" -> 1,
      "microservice.services.cachable.session-cache.port"      -> server.port(),
      "microservice.services.cachable.session-cache.host"      -> "127.0.0.1",
      "cookie.encryption.key"                                  -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "sso.encryption.key"                                     -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "queryParameter.encryption.key"                          -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "json.encryption.key"                                    -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "metrics.enabled"                                        -> false
    )
    .build()

  val sessionId: Option[SessionId]                                  = Some(SessionId("session-00000000-0000-0000-0000-000000000000"))
  lazy val addressJourneyCachingHelper: AddressJourneyCachingHelper =
    app.injector.instanceOf[AddressJourneyCachingHelper]

  def assertContainsLink(doc: Document, text: String, href: String): Assertion =
    assert(
      doc.getElementsContainingText(text).attr("href").contains(href),
      s"\n\nLink $href was not rendered on the page\n"
    )

  def taxCreditsBrokerResponse(excluded: Boolean) = s"""{"excluded": $excluded}"""

  val cacheMap = s"/keystore/pertax-frontend"

  val citizenDetailsUrl = s"/citizen-details/nino/$generatedNino"

  val personDetailsUrl = s"/citizen-details/$generatedNino/designatory-details"

  private def beforeEachAddressTaxCreditsBrokerCallToggleOn(): Unit = {
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
      get(urlEqualTo(citizenDetailsUrl))
        .willReturn(
          ok(FileHelper.loadFileInterpolatingNino("./it/resources/citizen-details.json", generatedNino))
        )
    )

    server.stubFor(
      get(urlEqualTo(personDetailsUrl))
        .willReturn(
          ok(FileHelper.loadFileInterpolatingNino("./it/resources/person-details.json", generatedNino))
        )
    )
  }

  "/personal-account/your-address/tax-credits-choice" must {
    val url          = "/personal-account/your-address/tax-credits-choice"
    val tcsBrokerUrl = s"/tcs/$generatedNino/exclusion"

    "redirect to the tax credits interstitial page if tax credits broker returns excluded flag as false" in {
      beforeEachAddressTaxCreditsBrokerCallToggleOn()
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(ok(taxCreditsBrokerResponse(excluded = false)))
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe SEE_OTHER
      result.get.futureValue.header.headers.get("Location") mustBe Some(
        "/personal-account/your-address/change-address-tax-credits"
      )
    }

    "render the do you get tax credits page if tax credits broker returns excluded flag as true" in {
      beforeEachAddressTaxCreditsBrokerCallToggleOn()
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(ok(taxCreditsBrokerResponse(excluded = true)))
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe OK
      contentAsString(result.get).contains("Do you get tax credits?") mustBe true
    }

    "redirect to the do you live in the uk page if tax credits broker returns not found response" in {
      beforeEachAddressTaxCreditsBrokerCallToggleOn()
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(notFound)
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe SEE_OTHER
      result.get.futureValue.header.headers.get("Location") mustBe Some(
        "/personal-account/your-address/residential/do-you-live-in-the-uk"
      )
    }

    "render the do you get tax credits page if tax credits broker returns error response other than not found" in {
      beforeEachAddressTaxCreditsBrokerCallToggleOn()
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(serverError)
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe OK
      contentAsString(result.get).contains("Do you get tax credits?") mustBe true
    }

    "Show the tax credits question when the AddressTaxCreditsBrokerCallToggle is false" in {

      lazy val messagesApi                 = app.injector.instanceOf[MessagesApi]
      implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

      server.stubFor(
        get(urlEqualTo(citizenDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/citizen-details.json")))
      )

      server.stubFor(
        get(urlEqualTo(personDetailsUrl))
          .willReturn(
            ok(FileHelper.loadFileInterpolatingNino("./it/resources/person-details.json", generatedNino))
          )
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
  }
}
