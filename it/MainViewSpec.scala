/*
 * Copyright 2020 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{ok, urlMatching}
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models._
import models.admin.{FeatureFlag, SCAWrapperToggle}
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.Assertion
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.twirl.api.Html
import testUtils.IntegrationSpec
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.sca.models.{MenuItemConfig, PtaMinMenuConfig, WrapperDataResponse}
import views.html.MainView

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

class MainViewSpec extends IntegrationSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID()}")))

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      Map(
        "cookie.encryption.key"                                         -> "gvBoGdgzqG1AarzF1LY0zQ==",
        "sso.encryption.key"                                            -> "gvBoGdgzqG1AarzF1LY0zQ==",
        "queryParameter.encryption.key"                                 -> "gvBoGdgzqG1AarzF1LY0zQ==",
        "json.encryption.key"                                           -> "gvBoGdgzqG1AarzF1LY0zQ==",
        "metrics.enabled"                                               -> false,
        "sca-wrapper.services.single-customer-account-wrapper-data.url" -> s"http://localhost:${server.port()}"
      )
    )
    .build()

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

  implicit lazy val configDecorator: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  val wrapperDataResponse: String = Json
    .toJson(
      WrapperDataResponse(
        Seq(
          MenuItemConfig("id", "NewLayout Item", "link", leftAligned = false, 0, None, None),
          MenuItemConfig("signout", "Sign out", "link", leftAligned = false, 0, None, None)
        ),
        PtaMinMenuConfig("MenuName", "BackName")
      )
    )
    .toString

  trait LocalSetup {

    when(mockFeatureFlagService.get(SCAWrapperToggle))
      .thenReturn(Future.successful(FeatureFlag(SCAWrapperToggle, isEnabled = true)))

    server.stubFor(
      WireMock
        .get(urlMatching("/single-customer-account-wrapper-data/message-data.*"))
        .willReturn(ok(s"""{"count": 0}"""))
    )

    server.stubFor(
      WireMock
        .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
        .willReturn(ok(wrapperDataResponse))
    )

    def buildUserRequest[A](
      nino: Option[Nino] = Some(testNino),
      userName: Option[UserName] = Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
      saUser: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(
        SaUtr(new SaUtrGenerator().nextSaUtr.utr)
      ),
      credentials: Credentials = Credentials("", UserDetails.GovernmentGatewayAuthProvider),
      confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200,
      personDetails: Option[PersonDetails] = Some(fakePersonDetails),
      trustedHelper: Option[TrustedHelper] = None,
      profile: Option[String] = None,
      messageCount: Option[Int] = None,
      request: Request[A] = FakeRequest().asInstanceOf[Request[A]]
    ): UserRequest[A] =
      UserRequest(
        nino,
        userName,
        saUser,
        credentials,
        confidenceLevel,
        personDetails,
        trustedHelper,
        Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", new SaUtrGenerator().nextSaUtr.utr)), "Activated")),
        profile,
        messageCount,
        None,
        request
      )

    def buildUserRequestNoSA[A](
      nino: Option[Nino] = Some(testNino),
      userName: Option[UserName] = Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
      saUser: SelfAssessmentUserType = NonFilerSelfAssessmentUser,
      credentials: Credentials = Credentials("", UserDetails.GovernmentGatewayAuthProvider),
      confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200,
      personDetails: Option[PersonDetails] = Some(fakePersonDetails),
      trustedHelper: Option[TrustedHelper] = None,
      profile: Option[String] = None,
      messageCount: Option[Int] = None,
      request: Request[A] = FakeRequest().asInstanceOf[Request[A]]
    ): UserRequest[A] =
      UserRequest(
        nino,
        userName,
        saUser,
        credentials,
        confidenceLevel,
        personDetails,
        trustedHelper,
        Set(),
        profile,
        messageCount,
        None,
        request
      )

    implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest()

    def view: MainView = app.injector.instanceOf[MainView]

    val title       = "Fake page title"
    val heading     = "Fake page heading"
    val backLinkUrl = "/personal-details/test"
    val content     = "Main page content"

    def main: Html =
      view(
        pageTitle = title,
        serviceName = heading,
        sidebarContent = Some(Html("SidebarLinks")),
        scripts = Some(Html("script")),
        showBackLink = true,
        backLinkUrl = backLinkUrl
      )(Html(content))

    def doc: Document = Jsoup.parse(main.toString)

    def assertContainsText(doc: Document, text: String): Assertion =
      assert(doc.toString.contains(text), "\n\ntext " + text + " was not rendered on the page.\n")

    def assertContainsLink(doc: Document, text: String, href: String): Assertion =
      assert(
        doc.getElementsContainingText(text).attr("href").contains(href),
        s"\n\nLink $href was not rendered on the page\n"
      )
  }

  "Main" when {

    "rendering the view" must {
      "render the correct title" in new LocalSetup {
        doc.title() mustBe s"$title - ${messages("label.your_personal_tax_account_gov_uk")}"
      }

      "render the correct heading" in new LocalSetup {
        assertContainsText(doc, heading)
      }

      "render the welsh language toggle" in new LocalSetup {
        assertContainsLink(doc, "Cymraeg", "/hmrc-frontend/language/cy")
      }
    }

    "rendering the nav bar" must {

      "render the Account home button" in new LocalSetup {
        assertContainsLink(doc, messages("label.account_home"), "/personal-account")
      }

      "render the Messages link" in new LocalSetup {
        assertContainsLink(doc, "Messages", "/personal-account/messages")
      }

      "show the number of unread messages in the Messages link" when {

        "the sca-wrapper toggle is disabled" in new LocalSetup {
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle)))
            .thenReturn(Future.successful(FeatureFlag(SCAWrapperToggle, isEnabled = false)))

          val msgCount                                                           = 21
          override implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
            buildUserRequest(request = FakeRequest(), messageCount = Some(msgCount))

          doc.getElementsByClass("hmrc-notification-badge").first().text() must include(msgCount.toString)
        }
      }

      "render the Check progress link" in new LocalSetup {
        assertContainsLink(doc, "Check progress", "/track")
      }

      "render the Your Profile link" in new LocalSetup {
        assertContainsLink(doc, "Profile and settings", "/personal-account/profile-and-settings")
      }

      "render the BTA link" when {
        "the user is GG and has SA enrolments" in new LocalSetup {
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle)))
            .thenReturn(Future.successful(FeatureFlag(SCAWrapperToggle, isEnabled = false)))

          assertContainsLink(doc, "Business tax account", "/business-account")
        }
      }

      "do not render the BTA link" when {
        "the user is GG and not an SA user" in new LocalSetup {
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle)))
            .thenReturn(Future.successful(FeatureFlag(SCAWrapperToggle, isEnabled = false)))

          override implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequestNoSA()

          assert(doc.getElementsContainingText("Business tax account").isEmpty)
        }
      }

      "render the sign out link" in new LocalSetup {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle)))
          .thenReturn(Future.successful(FeatureFlag(SCAWrapperToggle, isEnabled = false)))

        val href: String = controllers.routes.ApplicationController
          .signout(Some(RedirectUrl(configDecorator.getFeedbackSurveyUrl(configDecorator.defaultOrigin))), None)
          .url

        assertContainsLink(doc, messages("global.label.sign_out"), href)
      }
    }

    "displaying the page body" must {

      "render the back link" in new LocalSetup {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(SCAWrapperToggle)))
          .thenReturn(Future.successful(FeatureFlag(SCAWrapperToggle, isEnabled = false)))

        val backLink: Element = doc.getElementById("back-link")

        backLink.attr("href") mustBe backLinkUrl
        backLink.text() mustBe messages("label.back")
      }

      "render the trusted helpers banner" when {

        "a trusted helper is set in the request" in new LocalSetup {
          val principalName                                                      = "John Doe"
          val url                                                                = "/return-url"
          val helper: TrustedHelper = TrustedHelper(
            principalName,
            "Attorney name",
            url,
            generator.nextNino.nino
          )
          override implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
            buildUserRequest(request = FakeRequest(), trustedHelper = Some(helper))

          doc.getElementById("attorneyBanner") mustBe an[Element]

          assertContainsText(doc, principalName)
          assertContainsLink(doc, "Return to your account", "/return-url")
        }
      }

      "render given content" in new LocalSetup {
        assertContainsText(doc, content)
      }
    }
  }
}
