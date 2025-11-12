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

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.TaxComponentsRetrievalToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.*
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, status as httpStatus, writeableOf_AnyContentAsEmpty}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.time.TaxYear

import java.util.UUID
import scala.concurrent.Future

class HomeControllerMarriageAllowanceISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.taxcalc-frontend.port" -> server.port(),
      "microservice.services.tai.port"              -> server.port()
    )
    .build()

  val url                       = s"/personal-account"
  private val startTaxYear: Int = TaxYear.current.startYear

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
      .thenReturn(
        Future.successful(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true))
      )
  }

  "personal-account" must {
    "show correct message on the Marriage Allowance tile when transferring Personal Allowance to partner" in {

      val taxComponentsJson = Json
        .parse("""{
                 |   "data" : [ {
                 |      "componentType" : "MarriageAllowanceTransferred",
                 |      "employmentId" : 12,
                 |      "amount" : 12321,
                 |      "inputAmount" : 12321,
                 |      "description" : "Personal Allowance transferred to partner",
                 |      "iabdCategory" : "Deduction"
                 |   } ],
                 |   "links" : [ ]
                 |}""".stripMargin)
        .toString

      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/$startTaxYear/tax-components"))
          .willReturn(ok(taxComponentsJson))
      )
      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK

      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/$startTaxYear/tax-components"))
      )

      contentAsString(result).contains(
        Messages("label.you_currently_transfer_part_of_your_personal_allowance_to_your_partner")
      ) mustBe true
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/$startTaxYear/tax-components"))
      )
    }

    "show correct message on the Marriage Allowance tile when receiving Personal Allowance from partner" in {

      val taxComponentsJson = Json
        .parse("""{
                 |   "data" : [ {
                 |      "componentType" : "MarriageAllowanceReceived",
                 |      "employmentId" : 12,
                 |      "amount" : 12321,
                 |      "inputAmount" : 12321,
                 |      "description" : "Personal Allowance transferred to partner",
                 |      "iabdCategory" : "Deduction"
                 |   } ],
                 |   "links" : [ ]
                 |}""".stripMargin)
        .toString

      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/$startTaxYear/tax-components"))
          .willReturn(ok(taxComponentsJson))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK

      contentAsString(result).contains(
        Messages("label.your_partner_currently_transfers_part_of_their_personal_allowance_to_you")
      ) mustBe true
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/$startTaxYear/tax-components"))
      )
    }

    "show correct message on the Marriage Allowance tile when not transferring or receiving Personal Allowance from partner" in {

      val taxComponentsJson = Json
        .parse("""{
                 |   "data" : [ {
                 |      "componentType" : "OtherAllowance",
                 |      "employmentId" : 12,
                 |      "amount" : 12321,
                 |      "inputAmount" : 12321,
                 |      "description" : "Personal Allowance transferred to partner",
                 |      "iabdCategory" : "Deduction"
                 |   } ],
                 |   "links" : [ ]
                 |}""".stripMargin)
        .toString

      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/$startTaxYear/tax-components"))
          .willReturn(ok(taxComponentsJson))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK

      contentAsString(result).contains(
        Messages("label.transfer_part_of_your_personal_allowance_to_your_partner_")
      ) mustBe true
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/$startTaxYear/tax-components"))
      )
    }
  }
}
