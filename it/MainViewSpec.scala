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

import config.ConfigDecorator
import connectors.EnrolmentsConnector
import controllers.auth.requests.UserRequest
import models._
import org.joda.time.LocalDate
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{Assertion, BeforeAndAfterEach}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.{EnrolmentStoreCachingService, LocalSessionCache}
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.renderer.TemplateRenderer
import views.html.MainView

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class MainViewSpec extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with BeforeAndAfterEach {

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val hc = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID()}")))


    override def beforeEach(): Unit = {
      super.beforeEach()
      cache.remove()
    }

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(Map(
        "cookie.encryption.key"         -> "gvBoGdgzqG1AarzF1LY0zQ==",
        "sso.encryption.key"            -> "gvBoGdgzqG1AarzF1LY0zQ==",
        "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
        "json.encryption.key"           -> "gvBoGdgzqG1AarzF1LY0zQ==",
        "metrics.enabled"               -> false
      ))

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

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
      Some(testNino)),
    Some(Address(
      Some("1 Fake Street"),
      Some("Fake Town"),
      Some("Fake City"),
      Some("Fake Region"),
      None,
      Some("AA1 1AA"),
      None,
      Some(new LocalDate(2015, 3, 15)),
      None,
      Some("Residential"),
      false
    )),
    None
  )

  lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  implicit val configDecorator: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]
  implicit val templateRenderer: TemplateRenderer = app.injector.instanceOf[TemplateRenderer]
  implicit val messages: Messages = MessagesImpl(Lang("en"), messagesApi).messages

  trait LocalSetup {

    def buildUserRequest[A](
                          nino: Option[Nino] = Some(testNino),
                          userName: Option[UserName] = Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
                          saUser: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
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
        None,
        request
      )

    implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest()

    def view: MainView = app.injector.instanceOf[MainView]

    val title = "Fake page title"
    val heading = "Fake page heading"
    val backLinkUrl = "/personal-details/test"
    val content = "Main page content"

    def main: Html = {
      view(
        title,
        Some(heading),
        showUserResearchBanner = true,
        Some(Html("SidebarLinks")),
        Some("sidebar-class"),
        supportLinkEnabled = true,
        Some(Html("script")),
        Some(Html("ScriptElement")),
        Some(backLinkUrl),
        Some(Html("AdditionalGaCalls")),
        printableDocument = true
      )(Html(content))
    }

    def doc: Document = Jsoup.parse(main.toString)

    def assertContainsText(doc: Document, text: String): Assertion =
      assert(doc.toString.contains(text), "\n\ntext " + text + " was not rendered on the page.\n")

    def assertContainsLink(doc: Document, text: String, href: String): Assertion =
      assert(
        doc.getElementsContainingText(text).attr("href").contains(href),
        s"\n\nLink $href was not rendered on the page\n")
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
        assertContainsLink(doc, "Cymraeg", "/personal-account/lang/cyGb")
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

        "unread message count is populated in the request" in new LocalSetup {
          val msgCount = 21
          override implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
            buildUserRequest(request = FakeRequest(), messageCount = Some(msgCount))

          doc.getElementsByAttributeValueMatching("aria-label", "Number of unread messages").text() must include(
            msgCount.toString)
        }
      }

      "render the Check progress link" in new LocalSetup {
        assertContainsLink(doc, "Check progress", "/track")
      }

      "render the Your Profile link" in new LocalSetup {
        assertContainsLink(doc, "Your profile", "/personal-account/your-profile")
      }

      "render the BTA link" when {
        "the user is GG and has SA enrolments" in new LocalSetup {
          assertContainsLink(doc, "Business tax account", "/business-account")
        }
      }

      "render the sign out link" in new LocalSetup {

        val href = controllers.routes.ApplicationController
          .signout(Some(RedirectUrl(configDecorator.getFeedbackSurveyUrl(configDecorator.defaultOrigin))), None)
          .url

        assertContainsLink(doc, messages("global.label.sign_out"), href)
      }
    }

    "displaying the page body" must {

      "render the back link" in new LocalSetup {

        val backLink = doc.getElementsByClass("link-back").first()

        backLink.attr("href") mustBe backLinkUrl
        backLink.text() mustBe messages("label.back")
      }

      "render the trusted helpers banner" when {

        "a trusted helper is set in the request" in new LocalSetup {
          val principalName = "John Doe"
          val url = "/return-url"
          val helper = TrustedHelper(
            principalName,
            "Attorney name",
            url,
            generator.nextNino.nino
          )
          override implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
            buildUserRequest(request = FakeRequest(), trustedHelper = Some(helper))

          doc.getElementById("attorneyBanner") mustBe an[Element]

          assertContainsText(doc, principalName)
          assertContainsLink(doc, "Return to your own account", "/return-url")
        }
      }

      "render given content" in new LocalSetup {
        assertContainsText(doc, content)
      }
    }
  }


  val cache: LocalSessionCache = app.injector.instanceOf[LocalSessionCache]
  val mockConnector: EnrolmentsConnector = mock[EnrolmentsConnector]

  val service = new EnrolmentStoreCachingService(cache, mockConnector)

  val saUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  "EnrolmentStoreCachingService" when {

    "getSaUserTypeFromCache is called" should {

      "only call the connector once" in {

        when(mockConnector.getUserIdsWithEnrolments(any())(any(), any())
        ) thenReturn Future.successful(Right(Seq[String]()))

        await(service.getSaUserTypeFromCache(saUtr))

        await(service.getSaUserTypeFromCache(saUtr))

        verify(mockConnector, times(1)).getUserIdsWithEnrolments(any())(any(), any())
      }
    }
  }
}
