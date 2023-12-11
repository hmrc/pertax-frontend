package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{BreathingSpaceIndicatorToggle, TaxComponentsToggle, TaxcalcToggle}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.i18n.Messages
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import testUtils.IntegrationSpec
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
      server.stubFor(get(urlPathEqualTo(breathingSpaceUrl)).willReturn(aResponse.withFixedDelay(500)))
      server.stubFor(get(urlEqualTo(taxComponentsUrl)).willReturn(aResponse.withFixedDelay(500)))
      server.stubFor(get(urlPathEqualTo(taxCalcUrl)).willReturn(aResponse.withFixedDelay(500)))

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
}
