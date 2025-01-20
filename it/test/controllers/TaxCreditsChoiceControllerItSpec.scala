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
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.UserAnswers
import models.admin.AddressTaxCreditsBrokerCallToggle
import models.dto.AddressPageVisitedDto
import org.jsoup.nodes.Document
import org.mockito.{ArgumentMatchers, Mockito}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{Assertion, BeforeAndAfterEach}
import play.api
import play.api.Application
import play.api.http.Status._
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import repositories.JourneyCacheRepository
import routePages.HasAddressAlreadyVisitedPage
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId, SessionKeys}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class TaxCreditsChoiceControllerItSpec extends IntegrationSpec with BeforeAndAfterEach {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port"                        -> server.port(),
      "microservice.services.citizen-details.port"             -> server.port(),
      "microservice.services.tcs-broker.port"                  -> server.port(),
      "microservice.services.tcs-broker.timeoutInMilliseconds" -> 1,
      "cookie.encryption.key"                                  -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "sso.encryption.key"                                     -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "queryParameter.encryption.key"                          -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "json.encryption.key"                                    -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "metrics.enabled"                                        -> false
    )
    .overrides(
      api.inject.bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
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

  val citizenDetailsUrl = s"/citizen-details/nino/$generatedNino"

  val personDetailsUrl = s"/citizen-details/$generatedNino/designatory-details"

  private def beforeEachAddressTaxCreditsBrokerCallToggleOn(): Unit = {

    server.stubFor(
      get(urlEqualTo(citizenDetailsUrl))
        .willReturn(
          ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/citizen-details.json", generatedNino))
        )
    )

    server.stubFor(
      get(urlEqualTo(personDetailsUrl))
        .willReturn(
          ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino))
        )
    )
  }

  override def beforeEach(): Unit = {
    Mockito.reset(mockJourneyCacheRepository)
    super.beforeEach()
    val userAnswers: UserAnswers = UserAnswers
      .empty("1")
      .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
    when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
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
        "/personal-account/your-address/residential/where-is-your-new-address"
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
          .willReturn(ok(FileHelper.loadFile("./it/test/resources/citizen-details.json")))
      )

      server.stubFor(
        get(urlEqualTo(personDetailsUrl))
          .willReturn(
            ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino))
          )
      )

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(ok(FileHelper.loadFile("./it/test/resources/dashboard-data.json")))
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe OK
      contentAsString(result.get) must include(messages("label.do_you_get_tax_credits"))
    }
  }
}
