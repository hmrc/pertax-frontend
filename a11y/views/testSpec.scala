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

package views

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{AddressChangeAllowedToggle, BreathingSpaceIndicatorToggle, GetPersonFromCitizenDetailsToggle}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import testUtils.{A11ySpec, FileHelper}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.sca.models.{MenuItemConfig, PtaMinMenuConfig, UrBanner, Webchat, WrapperDataResponse}
import uk.gov.hmrc.scalatestaccessibilitylinter.domain.OutputFormat

import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

class testSpec extends A11ySpec {

  case class ExpectedData(title: String)

  // scalastyle:off cyclomatic.complexity
  def getExpectedData(key: String): ExpectedData =
    key match {
      case "id-check-complete"                     =>
        ExpectedData("Service unavailable - Personal tax account - GOV.UK")
      case "sign-in"                               =>
        ExpectedData("Sign in - Personal tax account - GOV.UK")
      case "sa-home"                               =>
        ExpectedData("Your Self Assessment - Personal tax account - GOV.UK")
      case "sa-reset-password"                     =>
        ExpectedData("You need to reset your password - Personal tax account - GOV.UK")
      case "sa-sign-in-again"                      =>
        ExpectedData(
          "You need to sign back in to Government Gateway using different details - Personal tax account - GOV.UK"
        )
      case "sa-find-user-id"                       =>
        ExpectedData("Get help finding your user ID - Personal tax account - GOV.UK")
      case "sa-know-user-id"                       =>
        ExpectedData("Do you know the user ID? - Personal tax account - GOV.UK")
      case "sa-other-credentials"                  =>
        ExpectedData(
          "Do you know the user ID and password for the account you use for Self Assessment? - Personal tax account - GOV.UK"
        )
      case "sa-wrong-account"                      =>
        ExpectedData("You are not signed in to the right account - Personal tax account - GOV.UK")
      case "breathing-space"                       =>
        ExpectedData("BREATHING SPACE - Personal tax account - GOV.UK")
      case "child-benefit-home"                    =>
        ExpectedData(
          "Child Benefit - Personal tax account - GOV.UK"
        )
      case "print-letter"                          =>
        ExpectedData(
          "Print your National Insurance summary - Personal tax account - GOV.UK"
        )
      case "self-assessment-summary"               =>
        ExpectedData(
          "Self Assessment summary - Personal tax account - GOV.UK"
        )
      case "your-national-insurance-state-pension" =>
        ExpectedData("Your National Insurance and State Pension - Personal tax account - GOV.UK")
      case "where-is-postal-address"               =>
        ExpectedData(
          "Where is your new postal address? - Personal tax account - GOV.UK"
        )
      case "where-is-address"                      =>
        ExpectedData(
          "Where is your new address? - Personal tax account - GOV.UK"
        )
      case "profile-and-settings"                  =>
        ExpectedData(
          "Profile and settings - Personal tax account - GOV.UK"
        )
      case "/"                                     =>
        ExpectedData(
          "Account home - Personal tax account - GOV.UK"
        )

      case key => throw new RuntimeException(s"Expected data are missing for `$key`")
    }

  val urls: Map[String, ExpectedData] = Map(
    "/personal-account"                                                      -> getExpectedData("/"),
    "/personal-account/profile-and-settings"                                 -> getExpectedData("profile-and-settings"),
    "/personal-account/your-address/residential/where-is-your-new-address"   -> getExpectedData("where-is-address"),
    "/personal-account/your-address/postal/where-is-your-new-postal-address" -> getExpectedData(
      "where-is-postal-address"
    ),
    "/personal-account/your-national-insurance-state-pension"                -> getExpectedData(
      "your-national-insurance-state-pension"
    ),
    "/personal-account/self-assessment-summary"                              -> getExpectedData("self-assessment-summary"),
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

  val unauthUrls: Map[String, ExpectedData] = Map(
    "/personal-account/signin"                  -> getExpectedData("sign-in"),
    "/personal-account/identity-check-complete" -> getExpectedData("id-check-complete")
  )

  val messageCount: Int                    = Random.between(1, 100)
  val menuWrapperData: Seq[MenuItemConfig] = Seq(
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
  val wrapperDataResponse: String          = Json
    .toJson(
      WrapperDataResponse(
        menuWrapperData,
        PtaMinMenuConfig("MenuName", "BackName"),
        List.empty[UrBanner],
        List.empty[Webchat]
      )
    )
    .toString

  val personDetailsUrl: String = s"/citizen-details/$generatedNino/designatory-details"

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(BreathingSpaceIndicatorToggle))
      .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

    server.stubFor(
      get(urlEqualTo(personDetailsUrl))
        .willReturn(ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino)))
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

    server.stubFor(get(urlEqualTo("/delegation/get")).willReturn(notFound()))
  }

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port"                                      -> server.port(),
      "microservice.services.breathing-space-if-proxy.timeoutInMilliseconds" -> 4000,
      "microservice.services.taxcalc-frontend.port"                          -> server.port(),
      "microservice.services.tai.port"                                       -> server.port(),
      "microservice.services.fandf.port"                                     -> server.port(),
      "sca-wrapper.services.single-customer-account-wrapper-data.url"        -> s"http://localhost:${server.port()}"
    )
    .build()

  val uuid: String = UUID.randomUUID().toString

  val authResponseSA: String =
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
       |        },
       |       {
       |            "key":"HMRC-MTD-IT",
       |            "identifiers": [
       |                {
       |                    "key":"MTDITID",
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
    "calling authenticated pages" must
      urls.foreach { case (url, expectedData: ExpectedData) =>
        s"pass content checks at url $url" in {
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
            .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

          server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponseSA)))
          val result: Future[Result] = route(app, request(url)).get
          val content                = Jsoup.parse(contentAsString(result))

          content.title() mustBe expectedData.title

          status(result) mustBe OK

          contentAsString(result) must passAccessibilityChecks(OutputFormat.Verbose)

        }

      }

    "calling unauthenticated pages" must
      unauthUrls.foreach { case (url, expectedData: ExpectedData) =>
        s"pass content checks at url $url" in {
          server.stubFor(
            WireMock
              .get(urlMatching("/mdtp/journey/journeyId/1234"))
              .willReturn(ok(s""""{"journeyResult": "LockedOut"}""".stripMargin))
          )

          val result: Future[Result] = route(app, FakeRequest(GET, url)).get

          val content = Jsoup.parse(contentAsString(result))
          content.title() mustBe expectedData.title

          contentAsString(result) must passAccessibilityChecks(OutputFormat.Verbose)

        }
      }
  }
}
