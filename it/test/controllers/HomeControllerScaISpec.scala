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

import cats.data.EitherT
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import controllers.auth.requests.UserRequest
import models.admin.*
import models.{ActivatedOnlineFilerSelfAssessmentUser, Address, Person, PersonDetails, SelfAssessmentUserType, UserAnswers, UserDetails}
import org.jsoup.Jsoup
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import testUtils.IntegrationSpec
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{SessionKeys, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.sca.models.*

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

class HomeControllerScaISpec extends IntegrationSpec with MockitoSugar {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.taxcalc-frontend.port"                   -> server.port(),
      "microservice.services.tai.port"                                -> server.port(),
      "sca-wrapper.services.single-customer-account-wrapper-data.url" -> s"http://localhost:${server.port()}"
    )
    .build()

  val url: String  = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  val messageCount: Int           = Random.between(1, 100)
  val wrapperDataResponse: String = Json
    .toJson(
      WrapperDataResponse(
        Seq(
          MenuItemConfig(
            "home",
            "Home",
            "/home",
            leftAligned = true,
            position = 0,
            Some("hmrc-account-icon hmrc-account-icon--home"),
            None
          ),
          MenuItemConfig(
            "messages",
            "Messages",
            "/personal-account/messages",
            leftAligned = false,
            position = 0,
            None,
            None
          ),
          MenuItemConfig(
            "progress",
            "Progress",
            "/track",
            leftAligned = false,
            position = 1,
            None,
            None
          ),
          MenuItemConfig(
            "profile",
            "Profile and Settings",
            "/personal-account/profile-and-settings",
            leftAligned = false,
            position = 2,
            None,
            None
          ),
          MenuItemConfig(
            "signout",
            "Sign out",
            "/personal-account/sign-out",
            leftAligned = false,
            position = 4,
            None,
            None
          )
        ),
        PtaMinMenuConfig("MenuName", "BackName"),
        List.empty[UrBanner],
        List.empty[Webchat]
      )
    )
    .toString

  private val generator = new Generator(new Random())

  private val testNino: Nino = generator.nextNino

  val fakePersonDetails: PersonDetails = PersonDetails(
    Person(
      Some("John"),
      None,
      Some("Doe"),
      Some("JD"),
      Some("Mr"),
      None,
      Some("M"),
      Some(LocalDate.parse("1975-12-03")),
      Some(testNino)
    ),
    Some(
      Address(
        Some("1 Fake Street"),
        Some("Fake Town"),
        Some("Fake City"),
        Some("Fake Region"),
        None,
        Some("AA1 1AA"),
        None,
        Some(LocalDate.of(2015, 3, 15)),
        None,
        Some("Residential"),
        isRls = false
      )
    ),
    None
  )
  def buildUserRequest[A](
    authNino: Nino = testNino,
    saUser: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(
      SaUtr(new SaUtrGenerator().nextSaUtr.utr)
    ),
    credentials: Credentials = Credentials("", UserDetails.GovernmentGatewayAuthProvider),
    confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200,
    trustedHelper: Option[TrustedHelper] = None,
    profile: Option[String] = None,
    request: Request[A] = FakeRequest().asInstanceOf[Request[A]],
    userAnswers: UserAnswers = UserAnswers.empty
  ): UserRequest[A] =
    UserRequest(
      authNino,
      saUser,
      credentials,
      confidenceLevel,
      trustedHelper,
      Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", new SaUtrGenerator().nextSaUtr.utr)), "Activated")),
      profile,
      None,
      request,
      userAnswers
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = true)))
    when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
      .thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true))
      )
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(EnforcePaperlessPreferenceToggle)))
      .thenReturn(Future.successful(FeatureFlag(EnforcePaperlessPreferenceToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, isEnabled = true)))
  }

  "personal-account" when {

    "rendering the view" must {

      "render the correct title" in {
        server.stubFor(
          WireMock
            .get(urlMatching("/single-customer-account-wrapper-data/message-data.*"))
            .willReturn(ok(s"$messageCount"))
        )

        server.stubFor(
          WireMock
            .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
            .willReturn(ok(wrapperDataResponse))
        )

        val result: Future[Result] = route(app, request).get
        contentAsString(result) must include("Personal tax account")
      }

      "render the welsh language toggle" in {
        server.stubFor(
          WireMock
            .get(urlMatching("/single-customer-account-wrapper-data/message-data.*"))
            .willReturn(ok(s"$messageCount"))
        )

        server.stubFor(
          WireMock
            .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
            .willReturn(ok(wrapperDataResponse))
        )

        val result: Future[Result] = route(app, request).get
        contentAsString(result) must include("/hmrc-frontend/language/cy")
      }
      "rendering the nav bar" must {

        "render the Account home button" in {
          server.stubFor(
            WireMock
              .get(urlMatching("/single-customer-account-wrapper-data/message-data.*"))
              .willReturn(ok(s"$messageCount"))
          )

          server.stubFor(
            WireMock
              .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
              .willReturn(ok(wrapperDataResponse))
          )

          val result: Future[Result] = route(app, request).get
          contentAsString(result) must include(messages("label.account_home"))
          contentAsString(result) must include("/personal-account")
        }

        "render the Messages link" in {
          server.stubFor(
            WireMock
              .get(urlMatching("/single-customer-account-wrapper-data/message-data.*"))
              .willReturn(ok(s"$messageCount"))
          )

          server.stubFor(
            WireMock
              .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
              .willReturn(ok(wrapperDataResponse))
          )

          val result: Future[Result] = route(app, request).get
          contentAsString(result) must include("Messages")
          contentAsString(result) must include("/personal-account/messages")
        }

        "show the number of unread messages in the Messages link" when {

          "unread message count is populated in the request" in {
            server.stubFor(
              WireMock
                .get(urlMatching("/single-customer-account-wrapper-data/message-data.*"))
                .willReturn(ok(s"$messageCount"))
            )

            server.stubFor(
              WireMock
                .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
                .willReturn(ok(wrapperDataResponse))
            )

            val result: Future[Result] = route(app, request).get
            contentAsString(result) must include(s"""<span class="hmrc-notification-badge">$messageCount</span>""")

            server.verify(0, getRequestedFor(urlEqualTo("/messages/count?read=No")))
            server.verify(1, getRequestedFor(urlMatching("/single-customer-account-wrapper-data/message-data.*")))
            server.verify(1, getRequestedFor(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*")))
          }
        }

        "render the Check progress link" in {
          server.stubFor(
            WireMock
              .get(urlMatching("/single-customer-account-wrapper-data/message-data.*"))
              .willReturn(ok(s"$messageCount"))
          )

          server.stubFor(
            WireMock
              .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
              .willReturn(ok(wrapperDataResponse))
          )

          val result: Future[Result] = route(app, request).get
          contentAsString(result) must include("Progress")
          contentAsString(result) must include("/track")
        }

        "render the Your Profile link" in {
          server.stubFor(
            WireMock
              .get(urlMatching("/single-customer-account-wrapper-data/message-data.*"))
              .willReturn(ok(s"$messageCount"))
          )

          server.stubFor(
            WireMock
              .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
              .willReturn(ok(wrapperDataResponse))
          )

          val result: Future[Result] = route(app, request).get
          contentAsString(result) must include("Profile and Settings")
          contentAsString(result) must include("/personal-account/profile-and-settings")
        }
      }

      "render the beta link" in {

        val result: Future[Result] = route(app, request).get
        val content                = Jsoup.parse(contentAsString(result))

        content.getElementsByClass("govuk-phase-banner").get(0).text() must include("Beta")

        content.getElementsByClass("govuk-phase-banner").get(0).html() must include(
          "/contact/beta-feedback?service=PTA"
        )
      }
    }
  }
}
