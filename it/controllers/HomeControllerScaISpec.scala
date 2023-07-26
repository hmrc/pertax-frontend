package controllers

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.admin._
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.mockito.MockitoSugar.mock
import play.api.{Application, inject}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import services.admin.FeatureFlagService
import testUtils.{IntegrationSpec, WireMockHelper}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.sca.models.{MenuItemConfig, PtaMinMenuConfig, WrapperDataResponse}

import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

class HomeControllerScaISpec extends IntegrationSpec with MockitoSugar {

  val mockFeatureFlagService = mock[FeatureFlagService]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(inject.bind[FeatureFlagService].toInstance(mockFeatureFlagService))
    .configure(
      "feature.breathing-space-indicator.enabled"                     -> true,
      "feature.breathing-space-indicator.timeoutInSec"                -> 4,
      "microservice.services.taxcalc.port"                            -> server.port(),
      "microservice.services.tai.port"                                -> server.port(),
      "sca-wrapper.services.single-customer-account-wrapper-data.url" -> s"http://localhost:${server.port()}"
    )
    .build()

  val url: String  = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  val messageCount                                 = Random.nextInt(100) + 1
  val wrapperDataResponse                          = Json
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
        PtaMinMenuConfig("MenuName", "BackName")
      )
    )
    .toString

  override def beforeEach() = {
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle)))
      .thenReturn(Future.successful(FeatureFlag(SCAWrapperToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PaperlessInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(PaperlessInterruptToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcMakePaymentLinkToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcMakePaymentLinkToggle, true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsShutteringToggle)))
      .thenReturn(Future.successful(FeatureFlag(NpsShutteringToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsOutageToggle)))
      .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PertaxBackendToggle)))
      .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, true)))
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
    }
  }
}
