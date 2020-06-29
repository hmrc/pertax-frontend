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

package views.html

import config.{ConfigDecorator, LocalTemplateRenderer}
import controllers.auth.requests.UserRequest
import models.UserDetails
import org.jsoup.nodes.{Document, Element}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures.{buildFakeRequestWithVerify, fakeNino}
import util.UserRequestFixture.buildUserRequest

class MainViewSpec extends ViewSpec {

  override protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind(classOf[TemplateRenderer]).to(classOf[LocalTemplateRenderer])
      )
      .configure(configValues)

  trait LocalSetup {

    implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
    implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
    implicit val templateRenderer: TemplateRenderer = injected[TemplateRenderer]

    def view = injected[MainView]

    val title = "Fake page title"
    val heading = "Fake page heading"
    val backLinkUrl = "/personal-details/test"
    val content = "Main page content"

    def main: Html =
      view(
        title,
        Some(heading),
        showUserResearchBanner = true,
        Some(Html("SidebarLinks")),
        Some("sidebar-class"),
        supportLinkEnabled = true,
        Some(Html("script")),
        Some(Html("ScriptElement")),
        Some("article-class"),
        includeGridWrapper = true,
        Some(backLinkUrl),
        Some(Html("AdditionalGaCalls")),
        printableDocument = true
      )(Html(content))

    def doc: Document = asDocument(main.toString)

  }

  "Main" when {

    "rendering the view" should {
      "render the correct title" in new LocalSetup {
        doc.title() shouldBe s"$title - ${messages("label.your_personal_tax_account_gov_uk")}"
      }

      "render the correct heading" in new LocalSetup {
        assertContainsText(doc, heading)
      }

      "render the welsh language toggle" in new LocalSetup {
        assertContainsLink(doc, "Cymraeg", "/personal-account/lang/cyGb")
      }

      // Change to test for user research banner after COVID-19
      "render the Coronavirus information banner in place of the user research banner" in new LocalSetup {

        assertContainsLink(
          doc,
          messages("label.url_coronavirus"),
          configDecorator.bannerLinkUrl.getOrElse("URL not found"))
      }
    }

    "rendering the nav bar" should {

      "render the Account home button" in new LocalSetup {
        assertContainsLink(doc, messages("label.account_home"), "/personal-account")
      }

      "render the Messages link" in new LocalSetup {
        assertContainsLink(doc, "Messages", "/personal-account/messages")
      }

      "show the number of unread messages in the Messages link" when {

        "unread message count is populated in the request" in new LocalSetup {
          val msgCount = 21
          override implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
            buildUserRequest(request = FakeRequest(), messageCount = Some(msgCount))

          doc.getElementsByAttributeValueMatching("aria-label", "Number of unread messages").text() should include(
            msgCount.toString)
        }
      }

      "render the Check progress link" in new LocalSetup {
        assertContainsLink(doc, "Check progress", "/track")
      }

      "render the Your account dropdown link" in new LocalSetup {
        assertContainsLink(doc, "Your account", "#subnav-your-account")
      }

      "render the Security settings subnav link if user is verify" in new LocalSetup {
        override implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            request = buildFakeRequestWithVerify("GET"),
            credentials = Credentials("", UserDetails.VerifyAuthProvider)
          )

        assertContainsLink(doc, "Manage your trusted helpers", "/trusted-helpers/select-a-service")
      }

      "render the Paperless settings subnav link if user is GG" in new LocalSetup {
        assertContainsLink(doc, "Manage your paperless settings", "/personal-account/preferences")
      }

      "render the Personal details subnav link if user is GG" in new LocalSetup {

        assertContainsLink(doc, "Manage your personal details", "/personal-account/personal-details")
      }

      "render the Sign-in details subnav link" when {
        "user is GG and SCP link is present" in new LocalSetup {
          val profileUrl = "/profile"
          override implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
            buildUserRequest(
              request = FakeRequest(),
              profile = Some(profileUrl)
            )

          assertContainsLink(doc, "Manage your Government Gateway details", profileUrl)
        }
      }

      "render the BTA link" when {
        "the user is GG and has SA enrolments" in new LocalSetup {
          assertContainsLink(doc, "Business tax account", "/business-account")
        }
      }

      "render the sign out link" in new LocalSetup {

        val href = controllers.routes.ApplicationController
          .signout(Some(SafeRedirectUrl(configDecorator.getFeedbackSurveyUrl(configDecorator.defaultOrigin))), None)
          .url

        assertContainsLink(doc, messages("global.label.sign_out"), href)
      }
    }

    "displaying the page body" should {

      "render the back link" in new LocalSetup {

        val backLink = doc.getElementsByClass("link-back").first()

        backLink.attr("href") shouldBe backLinkUrl
        backLink.text() shouldBe messages("label.back")
      }

      "render the trusted helpers banner" when {

        "a trusted helper is set in the request" in new LocalSetup {
          val principalName = "John Doe"
          val url = "/return-url"
          val helper = TrustedHelper(
            principalName,
            "Attorney name",
            url,
            fakeNino.nino
          )
          override implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
            buildUserRequest(request = FakeRequest(), trustedHelper = Some(helper))

          doc.getElementById("attorneyBanner") shouldBe an[Element]

          assertContainsText(doc, principalName)
          assertContainsLink(doc, "Return to your own account", "/return-url")
        }
      }

      "render given content" in new LocalSetup {
        assertContainsText(doc, content)
      }
    }
  }
}
