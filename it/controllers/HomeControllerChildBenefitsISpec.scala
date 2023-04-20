package controllers

import models.admin.{ChildBenefitSingleAccountToggle, SingleAccountCheckToggle, TaxcalcToggle}
import play.api.Application
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import play.api.test.{FakeRequest, Helpers}
import services.admin.FeatureFlagService
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys

import java.util.UUID
import scala.concurrent.Future

class HomeControllerChildBenefitsISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"      -> true,
      "feature.breathing-space-indicator.timeoutInSec" -> 4,
      "microservice.services.taxcalc.port"             -> server.port(),
      "microservice.services.tai.port"                 -> server.port()
    )
    .build()

  val url: String  = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  override def beforeEach(): Unit = {
    server.resetAll()
    beforeEachHomeController()

    lazy val featureFlagService = app.injector.instanceOf[FeatureFlagService]
    featureFlagService.set(TaxcalcToggle, enabled = false).futureValue
    featureFlagService.set(SingleAccountCheckToggle, enabled = true).futureValue
    featureFlagService.set(ChildBenefitSingleAccountToggle, enabled = true).futureValue
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
      contentAsString(resultSingleChildBenefit.get) must include("Make or manage a Child Benefit claim")
      contentAsString(resultSingleChildBenefit.get) must include("Make a claim")
      contentAsString(resultSingleChildBenefit.get) must include("Manage a claim")
    }
  }
}
