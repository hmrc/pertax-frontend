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

import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, post, urlEqualTo}
import models.admin.DfsFormsFrontendAvailabilityToggle
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

class HomeControllerNISPISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure("microservice.services.dfs-digital-forms-frontend.port" -> server.port())
    .build()

  private val ptaUrl            = "/personal-account"
  private val dfsPartialNinoUrl = "/digital-forms/forms/personal-tax/national-insurance/catalogue"
  val uuid: String              = UUID.randomUUID().toString
  private val dummyContent      = "National Insurance forms"

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, ptaUrl).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController()
  }

  "personal-account" must {
    "show NISP tile and take the user to NISP Bucket page" when {
      "dfs-digital-forms-frontend-available-toggle is enabled so that user can view and save National Insurance number" in {

        server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(saUTRActivatedAuthResponse)))

        server.stubFor(
          get(urlEqualTo(dfsPartialNinoUrl))
            .willReturn(ok(dummyContent))
        )

        val result: Future[Result] = route(app, request).get
        httpStatus(result) mustBe OK
        contentAsString(result).contains(Messages("label.your_national_insurance_and_state_pension")) mustBe true

        val nispViewURL              = "/personal-account/your-national-insurance-state-pension"
        val nispViewReq              = FakeRequest(GET, nispViewURL)
          .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")

        val resultSa: Future[Result] = route(app, nispViewReq).get
        httpStatus(resultSa) mustBe OK
        contentAsString(resultSa).contains(Messages("label.view_and_save_your_national_insurance_number")) mustBe true
        contentAsString(resultSa).contains(Messages("label.view_your_state_pension_summary")) mustBe true
        contentAsString(resultSa).contains(Messages("label.view_your_national_insurance_summary")) mustBe true
        contentAsString(resultSa).contains(Messages("label.find_out_more_about_national_insurance_")) mustBe true
      }

      "dfs-digital-forms-frontend-available-toggle is disabled and page is not displaying the National Insurance forms section" in {

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsFormsFrontendAvailabilityToggle)))
          .thenReturn(Future.successful(FeatureFlag(DfsFormsFrontendAvailabilityToggle, isEnabled = false)))

        server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(saUTRActivatedAuthResponse)))

        val result: Future[Result] = route(app, request).get
        httpStatus(result) mustBe OK
        contentAsString(result).contains(Messages("label.your_national_insurance_and_state_pension")) mustBe true

        val nispViewURL              = "/personal-account/your-national-insurance-state-pension"
        val nispViewReq              = FakeRequest(GET, nispViewURL)
          .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")

        val resultSa: Future[Result] = route(app, nispViewReq).get
        httpStatus(resultSa) mustBe OK
        contentAsString(resultSa).contains("National Insurance forms") mustBe false
      }
    }
  }
}
