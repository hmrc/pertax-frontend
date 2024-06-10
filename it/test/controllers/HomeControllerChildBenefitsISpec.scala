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
      "feature.breathing-space-indicator.enabled"   -> true,
      "microservice.services.taxcalc-frontend.port" -> server.port(),
      "microservice.services.tai.port"              -> server.port()
    )
    .build()

  val url: String  = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController()
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
