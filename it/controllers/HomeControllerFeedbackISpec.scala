package controllers

import models.admin.{NationalInsuranceTileToggle, NpsOutageToggle, NpsShutteringToggle, PaperlessInterruptToggle, PertaxBackendToggle, RlsInterruptToggle, SingleAccountCheckToggle, TaxComponentsToggle, TaxSummariesTileToggle, TaxcalcMakePaymentLinkToggle, TaxcalcToggle}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.util.UUID
import scala.concurrent.Future

class HomeControllerFeedbackISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"      -> true,
      "feature.breathing-space-indicator.timeoutInSec" -> 4,
      "microservice.services.taxcalc.port"             -> server.port(),
      "microservice.services.tai.port"                 -> server.port()
    )
    .build()

  val url = s"/personal-account"

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  override def beforeEach(): Unit = {
    server.resetAll()
    beforeEachHomeController()

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PertaxBackendToggle)))
      .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsOutageToggle)))
      .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PaperlessInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(PaperlessInterruptToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcMakePaymentLinkToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcMakePaymentLinkToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsShutteringToggle)))
      .thenReturn(Future.successful(FeatureFlag(NpsShutteringToggle, false)))
  }

  "personal-account" must {
    "show the correct feedback link" in {
      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(
        Messages("global.label.this_is_a_new_service_your_feedback_will_help_us_to_improve_it_link_text")
      ) mustBe true
      contentAsString(result).contains("/contact/beta-feedback-unauthenticated") mustBe true
    }
  }
}
