package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{AddressTaxCreditsBrokerCallToggle, BreathingSpaceIndicatorToggle, TaxComponentsToggle, TaxcalcToggle}
import models.{Person, PersonDetails}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.OK
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future

class HomeControllerTimeoutsISpec extends IntegrationSpec {
  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"                            -> true,
      "microservice.services.breathing-space-if-proxy.port"                  -> server.port(),
      "microservice.services.breathing-space-if-proxy.timeoutInMilliseconds" -> 1,
      "microservice.services.tai.port"                                       -> server.port(),
      "microservice.services.tai.timeoutInMilliseconds"                      -> 1,
      "microservice.services.taxcalc.port"                                   -> server.port(),
      "microservice.services.taxcalc.timeoutInMilliseconds"                  -> 1
    )
    .build()

  private def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, "/personal-account")
      .withSession(SessionKeys.sessionId -> UUID.randomUUID().toString, SessionKeys.authToken -> "1")

  private val breathingSpaceUrl                            = s"/$generatedNino/memorandum"
  private val taxComponentsUrl                             = s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"
  private val taxCalcUrl                                   = s"/taxcalc/$generatedNino/reconciliations"

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController(memorandum = false)

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
      .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, isEnabled = true)))
  }

  "personal-account" must {
    val urlBreathingSpace = controllers.routes.InterstitialController.displayBreathingSpaceDetails.url
    "hide relevant component(s) when HOD calls time out (breathing space, tax components, tax calc" in {
      server.stubFor(get(urlPathEqualTo(breathingSpaceUrl)).willReturn(aResponse.withFixedDelay(100)))
      server.stubFor(get(urlEqualTo(taxComponentsUrl)).willReturn(aResponse.withFixedDelay(100)))
      server.stubFor(get(urlPathEqualTo(taxCalcUrl)).willReturn(aResponse.withFixedDelay(100)))

      val result: Future[Result] = route(app, request).get

      // Breathing space:-
      contentAsString(result).contains(Messages("label.breathing_space")) mustBe false
      contentAsString(result).contains(urlBreathingSpace) mustBe false
      server.verify(1, getRequestedFor(urlEqualTo(breathingSpaceUrl)))

      // Tax components (marriage allowance):-
      contentAsString(result).contains(
        Messages("label.transfer_part_of_your_personal_allowance_to_your_partner_")
      ) mustBe true
      server.verify(1, getRequestedFor(urlEqualTo(taxComponentsUrl)))

      contentAsString(result).contains(Messages("label.you_paid_the_right_amount_of_tax")) mustBe false
      contentAsString(result).contains(Messages("label.your_tax_has_not_been_calculated")) mustBe false
      contentAsString(result).contains(Messages("label.find_out_why_you_paid_too_much")) mustBe false
      contentAsString(result).contains(Messages("label.make_a_payment")) mustBe false
      server.verify(1, getRequestedFor(urlEqualTo(taxCalcUrl)))
    }
  }

  "/personal-account/your-address/tax-credits-choice" must {
    "render the do you get tax credits page when HOD calls time out" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true)))
      server.stubFor(
        get(urlPathMatching("/keystore/pertax-frontend/.*"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                """{"id": "session-id","data": {"addressPageVisitedDto": {"hasVisitedPage": true}}}""".stripMargin
              )
          )
      )
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
      )
      server.stubFor(
        get(urlPathEqualTo(s"/tcs/$generatedNino/exclusion"))
          .willReturn(aResponse.withFixedDelay(100))
      )

      val request = FakeRequest(GET, "/personal-account/your-address/tax-credits-choice")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe OK
      contentAsString(result.get).contains("Do you get tax credits?") mustBe true
    }
  }
}

class HomeControllerTimeoutsCitizenDetailsISpec extends IntegrationSpec {
  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"                   -> true,
      "microservice.services.citizen-details.port"                  -> server.port(),
      "microservice.services.citizen-details.timeoutInMilliseconds" -> 0
    )
    .build()

  private def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, "/personal-account")
      .withSession(SessionKeys.sessionId -> UUID.randomUUID().toString, SessionKeys.authToken -> "1")

  private val citizenDetailsUrl                            = s"/citizen-details/$generatedNino/designatory-details"

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController(memorandum = false)

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
      .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, isEnabled = true)))
  }

  "personal-account" must {
    "show person name when citizen details does NOT time out" in {
      val personDetails: PersonDetails =
        PersonDetails(
          Person(
            Some("Firstname"),
            Some("Middlename"),
            Some("Lastname"),
            None,
            None,
            None,
            None,
            None,
            None
          ),
          None,
          None
        )
      server.stubFor(get(urlPathEqualTo(citizenDetailsUrl)).willReturn(ok(Json.toJson(personDetails).toString())))

      val result: Future[Result] = route(app, request).get
      val content                = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("govuk-heading-xl").get(0).text() mustBe "Firstname Lastname"
      server.verify(1, getRequestedFor(urlEqualTo(citizenDetailsUrl)))
    }

    "show no person name when citizen details times out" in {
      server.stubFor(get(urlPathEqualTo(citizenDetailsUrl)).willReturn(aResponse.withFixedDelay(100)))
      val result: Future[Result] = route(app, request).get
      val content                = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("govuk-heading-xl").get(0).text() mustBe ""
      server.verify(1, getRequestedFor(urlEqualTo(citizenDetailsUrl)))
    }
  }
}
