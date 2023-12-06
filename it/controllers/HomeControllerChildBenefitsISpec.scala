package controllers

import models.admin.SingleAccountCheckToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import play.api.test.{FakeRequest, Helpers}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.util.UUID
import scala.concurrent.Future

class HomeControllerChildBenefitsISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"                            -> true,
      "microservice.services.breathing-space-if-proxy.timeoutInMilliseconds" -> 4,
      "microservice.services.taxcalc.port"                                   -> server.port(),
      "microservice.services.tai.port"                                       -> server.port()
    )
    .build()

  val url: String  = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController()

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, isEnabled = true)))
  }

  "personal-account" must {
    val urlSingleChildBenefit = routes.InterstitialController.displayChildBenefitsSingleAccountView.url
    "show the the child benefit tile with the correct single account link" in {
      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(
        Messages("label.child_benefit")
      ) mustBe true
      contentAsString(result).contains(
        Messages("label.a_payment_to_help_with_the_cost_of_bringing_up_children")
      ) mustBe true
      contentAsString(result).contains(
        urlSingleChildBenefit
      ) mustBe true

      val requestSingleChildBenefit = FakeRequest(GET, urlSingleChildBenefit)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSingleChildBenefit  = route(app, requestSingleChildBenefit)

      Helpers.status(resultSingleChildBenefit.get) mustBe OK
      contentAsString(resultSingleChildBenefit.get) must include(Messages("label.check_if_you_can_claim"))
      contentAsString(resultSingleChildBenefit.get) must include(Messages("label.making_a_claim"))
      contentAsString(resultSingleChildBenefit.get) must include(Messages("label.change_your_bank_details"))
      contentAsString(resultSingleChildBenefit.get) must include(Messages("label.make_a_claim"))
      contentAsString(resultSingleChildBenefit.get) must include(Messages("label.manage_a_claim"))
      contentAsString(resultSingleChildBenefit.get) must include(
        Messages("label.report_changes_that_affect_your_child_benefit")
      )
      contentAsString(resultSingleChildBenefit.get) must include(
        Messages("label.view_your_child_benefit_payment_history")
      )
      contentAsString(resultSingleChildBenefit.get) must include(
        Messages("label.view_your_proof_of_entitlement_to_child_benefit")
      )
      contentAsString(resultSingleChildBenefit.get) must include(Messages("label.high_income_child_benefit_charge"))
    }
  }
}
