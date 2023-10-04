package views

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{BreathingSpaceIndicatorToggle, SCAWrapperToggle}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.sca.models.{MenuItemConfig, PtaMinMenuConfig, WrapperDataResponse}
import uk.gov.hmrc.scalatestaccessibilitylinter.domain.OutputFormat

import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

class testSpec extends IntegrationSpec {

  case class ExpectedData(title: String)
  def getExpectedData(key: String): ExpectedData =
    key match {
      case "id-check-complete"          =>
        ExpectedData("Service unavailable - Personal tax account - GOV.UK")
      case "sign-in"                    =>
        ExpectedData("Sign in - Personal tax account - GOV.UK")
      case "sa-home"                    =>
        ExpectedData("Your Self Assessment - Personal tax account - GOV.UK")
      case "sa-reset-password"          =>
        ExpectedData("You need to reset your password - Personal tax account - GOV.UK")
      case "sa-sign-in-again"           =>
        ExpectedData(
          "You need to sign back in to Government Gateway using different details - Personal tax account - GOV.UK"
        )
      case "sa-find-user-id"            =>
        ExpectedData("Get help finding your user ID - Personal tax account - GOV.UK")
      case "sa-know-user-id"            =>
        ExpectedData("Do you know the user ID? - Personal tax account - GOV.UK")
      case "sa-other-credentials"       =>
        ExpectedData(
          "Do you know the user ID and password for the account you use for Self Assessment? - Personal tax account - GOV.UK"
        )
      case "sa-wrong-account"           =>
        ExpectedData("You are not signed in to the right account - Personal tax account - GOV.UK")
      case "breathing-space"            =>
        ExpectedData("BREATHING SPACE - Personal tax account - GOV.UK")
      case "child-benefit-home"         =>
        ExpectedData(
          "Child Benefit - Personal tax account - GOV.UK"
        )
      case "print-letter"               =>
        ExpectedData(
          "Print your National Insurance summary - Personal tax account - GOV.UK"
        )
      case "self-assessment-summary"    =>
        ExpectedData(
          "Self Assessment summary - Personal tax account - GOV.UK"
        )
      case "national-insurance-summary" =>
        ExpectedData("National Insurance summary - Personal tax account - GOV.UK")
      case "postal-address-uk"          =>
        ExpectedData(
          "Is your postal address in the UK? - Personal tax account - GOV.UK"
        )
      case "change-address-tcs"         =>
        ExpectedData(
          "Change of address - Personal tax account - GOV.UK"
        )
      case "live-in-uk"                 =>
        ExpectedData(
          "Is your main address in the UK? - Personal tax account - GOV.UK"
        )
      case "profile-and-settings"       =>
        ExpectedData(
          "Profile and settings - Personal tax account - GOV.UK"
        )
      case "/"                          =>
        ExpectedData(
          "Account home - Personal tax account - GOV.UK"
        )

      case key => throw new RuntimeException(s"Expected data are missin for `$key`")
    }

  val urls = Map(
    "/personal-account"                                                      -> getExpectedData("/"),
    "/personal-account/profile-and-settings"                                 -> getExpectedData("profile-and-settings"),
    "/personal-account/your-address/residential/do-you-live-in-the-uk"       -> getExpectedData("live-in-uk"),
    "/personal-account/your-address/change-address-tax-credits"              -> getExpectedData("change-address-tcs"),
    "/personal-account/your-address/postal/is-your-postal-address-in-the-uk" -> getExpectedData("postal-address-uk"),
    "/personal-account/national-insurance-summary"                           -> getExpectedData("national-insurance-summary"),
    "/personal-account/self-assessment-summary"                              -> getExpectedData("self-assessment-summary"),
    "/personal-account/national-insurance-summary/print-letter"              -> getExpectedData("print-letter"),
    "/personal-account/child-benefit/home"                                   -> getExpectedData("child-benefit-home"),
    "/personal-account/breathing-space"                                      -> getExpectedData("breathing-space"),
    "/personal-account/self-assessment/signed-in-wrong-account"              -> getExpectedData("sa-wrong-account"),
    "/personal-account/self-assessment/do-you-know-other-credentials"        -> getExpectedData("sa-other-credentials"),
    "/personal-account/self-assessment/do-you-know-user-id"                  -> getExpectedData("sa-know-user-id"),
    "/personal-account/self-assessment/find-your-user-id"                    -> getExpectedData("sa-find-user-id"),
    "/personal-account/self-assessment/sign-in-again"                        -> getExpectedData("sa-sign-in-again"),
    "/personal-account/self-assessment/need-to-reset-password"               -> getExpectedData("sa-reset-password"),
    "/personal-account/self-assessment-home"                                 -> getExpectedData("sa-home")
  )

  val unauthUrls = Map(
    "/personal-account/signin"                  -> getExpectedData("sign-in"),
    "/personal-account/identity-check-complete" -> getExpectedData("id-check-complete")
  )

  val messageCount        = Random.between(1, 100)
  val menuWrapperData     = Seq(
    MenuItemConfig(
      "home",
      "Account Home",
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
  )
  val wrapperDataResponse = Json
    .toJson(
      WrapperDataResponse(menuWrapperData, PtaMinMenuConfig("MenuName", "BackName"))
    )
    .toString

  val personDetailsUrl: String = s"/citizen-details/$generatedNino/designatory-details"
  val tcsBrokerUrl             = s"/tcs/$generatedNino/dashboard-data"

  override def beforeEach() = {
    super.beforeEach()
    when(mockFeatureFlagService.get(SCAWrapperToggle))
      .thenReturn(Future.successful(FeatureFlag(SCAWrapperToggle, true)))
    when(mockFeatureFlagService.get(BreathingSpaceIndicatorToggle))
      .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, true)))

    server.stubFor(
      get(urlEqualTo(personDetailsUrl))
        .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
    )

    server.stubFor(
      put(urlMatching(s"/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
    )

    server.stubFor(
      get(urlPathMatching(s"$cacheMap/.*"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody("""
                        |{
                        |	"id": "session-id",
                        |	"data": {
                        |   "addressPageVisitedDto": {
                        |     "hasVisitedPage": true
                        |   }
                        |	},
                        |	"modifiedDetails": {
                        |		"createdAt": {
                        |			"$date": 1400258561678
                        |		},
                        |		"lastUpdated": {
                        |			"$date": 1400258561675
                        |		}
                        |	}
                        |}
                        |""".stripMargin)
        )
    )

    server.stubFor(
      WireMock
        .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
        .willReturn(ok(wrapperDataResponse))
    )

    server.stubFor(
      WireMock
        .get(urlMatching("/single-customer-account-wrapper-data/message-data.*"))
        .willReturn(ok(s"$messageCount"))
    )
  }

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"                     -> true,
      "feature.breathing-space-indicator.timeoutInSec"                -> 4,
      "microservice.services.taxcalc.port"                            -> server.port(),
      "microservice.services.tai.port"                                -> server.port(),
      "sca-wrapper.services.single-customer-account-wrapper-data.url" -> s"http://localhost:${server.port()}",
      "microservice.services.cachable.session-cache.port"             -> server.port(),
      "microservice.services.cachable.session-cache.host"             -> "127.0.0.1"
    )
    .build()

  val uuid = UUID.randomUUID().toString

  val cacheMap = s"/keystore/pertax-frontend"

  val authResponseSA =
    s"""
       |{
       |    "confidenceLevel": 200,
       |    "nino": "$generatedNino",
       |    "name": {
       |        "name": "John",
       |        "lastName": "Smith"
       |    },
       |    "loginTimes": {
       |        "currentLogin": "2021-06-07T10:52:02.594Z",
       |        "previousLogin": null
       |    },
       |    "optionalCredentials": {
       |        "providerId": "4911434741952698",
       |        "providerType": "GovernmentGateway"
       |    },
       |    "authProviderId": {
       |        "ggCredId": "xyz"
       |    },
       |    "externalId": "testExternalId",
       |    "allEnrolments": [
       |       {
       |          "key":"HMRC-PT",
       |          "identifiers": [
       |             {
       |                "key":"NINO",
       |                "value": "$generatedNino"
       |             }
       |          ]
       |       },
       |       {
       |            "key":"IR-SA",
       |            "identifiers": [
       |                {
       |                    "key":"UTR",
       |                    "value": "$generatedUtr"
       |                }
       |            ],
       |            "state": "Activated"
       |        }
       |    ],
       |    "affinityGroup": "Individual",
       |    "credentialStrength": "strong"
       |}
       |""".stripMargin

  def request(url: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "Bearer 1")

  "/personal-account/" when {
    "calling authenticated pages"   must {
      urls.foreach { case (url, expectedData: ExpectedData) =>
        s"pass content checks at url $url with SCA wrapper toggle set to true" in {
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle))) thenReturn Future.successful(
            FeatureFlag(SCAWrapperToggle, true)
          )
          server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponseSA)))
          val result: Future[Result] = route(app, request(url)).get
          val content                = Jsoup.parse(contentAsString(result))

          content.title() mustBe expectedData.title

          status(result) mustBe OK

          contentAsString(result) must passAccessibilityChecks(OutputFormat.Verbose)

        }
        s"pass content checks at url $url with SCA wrapper toggle set to false" in {
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle))) thenReturn Future.successful(
            FeatureFlag(SCAWrapperToggle, false)
          )
          server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponseSA)))
          val result: Future[Result] = route(app, request(url)).get
          val content                = Jsoup.parse(contentAsString(result))

          content.title() mustBe expectedData.title

          status(result) mustBe OK

          contentAsString(result) must passAccessibilityChecks(OutputFormat.Verbose)

        }

      }
    }
    "calling unauthenticated pages" must {
      unauthUrls.foreach { case (url, expectedData: ExpectedData) =>
        s"pass content checks at url $url with SCA wrapper toggle set to true" in {
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle))) thenReturn Future.successful(
            FeatureFlag(SCAWrapperToggle, true)
          )
          server.stubFor(
            WireMock
              .get(urlMatching("/mdtp/journey/journeyId/1234"))
              .willReturn(ok(s""""{"journeyResult": "LockedOut"}""".stripMargin))
          )

          val result: Future[Result] = route(app, FakeRequest(GET, url)).get

          val content                = Jsoup.parse(contentAsString(result))
          content.title() mustBe expectedData.title

          contentAsString(result) must passAccessibilityChecks(OutputFormat.Verbose)

        }
        s"pass content checks at url $url with SCA wrapper toggle set to false" in {
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle))) thenReturn Future.successful(
            FeatureFlag(SCAWrapperToggle, false)
          )
          server.stubFor(
            WireMock
              .get(urlMatching("/mdtp/journey/journeyId/1234"))
              .willReturn(ok(s""""{"journeyResult": "LockedOut"}""".stripMargin))
          )

          val result: Future[Result] = route(app, FakeRequest(GET, url)).get

          val content                = Jsoup.parse(contentAsString(result))
          content.title() mustBe expectedData.title

          contentAsString(result) must passAccessibilityChecks(OutputFormat.Verbose)

        }
      }
    }
  }
}
