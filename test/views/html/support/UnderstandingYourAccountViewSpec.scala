/*
 * Copyright 2025 HM Revenue & Customs
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

package views.html.support

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.bindable.Origin
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api
import play.api.Application
import play.api.i18n.Messages
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.JourneyCacheRepository
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

import scala.jdk.CollectionConverters.*

class UnderstandingYourAccountViewSpec extends ViewSpec {
  implicit val mockConfigDecorator: ConfigDecorator             = mock[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

  lazy val page: UnderstandingYourAccountView = inject[UnderstandingYourAccountView]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      api.inject.bind[ConfigDecorator].toInstance(mockConfigDecorator),
      api.inject.bind[JourneyCacheRepository].toInstance(mock[JourneyCacheRepository])
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)
    when(mockConfigDecorator.defaultOrigin).thenReturn(Origin("PERTAX"))
    when(mockConfigDecorator.pertaxFrontendBackLink).thenReturn("/goBack")
    when(mockConfigDecorator.personalAccount).thenReturn("/personal-account")
    when(mockConfigDecorator.notifyChangeOfDetails).thenReturn("/notify-changes-of-details")
    when(mockConfigDecorator.getFeedbackSurveyUrl(any())).thenReturn("/feedback/url")
  }

  "Rendering UnderstandingYourAccountView.scala.html" must {
    def renderedDocument(implicit messages: Messages): Document =
      asDocument(page()(userRequest, mockConfigDecorator, messages).toString)

    lazy val document: Document      = renderedDocument(messages)
    lazy val welshDocument: Document = renderedDocument(welshMessages)

    "show the expected title for Understanding your HMRC Online account page" in {
      document.select("title").asScala.exists(e => e.text contains "Understanding your HMRC Online account") mustBe true
    }

    "show the expected English headers for Understanding your HMRC Online account page" in {
      document.select("h1").asScala.exists(e => e.text == "Understanding your HMRC Online account") mustBe true
      document.select("h2").asScala.exists(e => e.text == "How to use your HMRC online account") mustBe true

      document.select("h3").asScala.map(_.text).toSeq mustBe Seq(
        "Your tasks",
        "Recent activity",
        "Taxes and benefits",
        "HMRC news",
        "HMRC support"
      )
    }

    "show the expected English paragraphs" in {
      assertContainsText(document, "Your HMRC online account gives you access to the following services:")
      assertContainsText(
        document,
        "You can use your account by selecting the different sections on your 'Account home' page."
      )
      assertContainsText(
        document,
        "Access tasks that must be completed, such as claiming refunds or paying tax bills."
      )
      assertContainsText(
        document,
        "Your HMRC Online account only displays your tasks for the following services:"
      )
      assertContainsText(document, "You may have other tasks:")
      assertContainsText(document, "View recent updates such as payments from your job or tax code changes.")
      assertContainsText(
        document,
        "Check the taxes and benefits you currently have and find out about others that may be relevant to you."
      )
      assertContainsText(document, "Get the latest updates from HMRC.")
      assertContainsText(document, "Get technical support and help with taxes and benefits.")
    }

    "show the expected English content and order for the access list" in {
      document.select("#accessList li").eachText().asScala.toSeq mustBe Seq(
        "Pay As You Earn (PAYE)",
        "Self Assessment",
        "Child Benefit",
        "Marriage Allowance",
        "National Insurance and State Pension",
        "Annual Tax Summary"
      )
    }

    "show the expected English content and order for the task list" in {
      document.select("#taskList li").eachText().asScala.toSeq mustBe Seq(
        "Pay As You Earn (PAYE)",
        "Self Assessment",
        "Child Benefit",
        "National Insurance and State Pension"
      )
    }

    "show the expected English content for the other list" in {
      document.select("#otherList li").eachText().asScala.toSeq mustBe Seq(
        "if you use other HMRC services (you might use different Government Gateway accounts for these)",
        "if your circumstances change (opens in new tab)",
        "if you have a Business Tax Account"
      )
    }

    "not render the old personal account link" in {
      document.select("a#personalAccountLink").size mustBe 0
      assertNotContainText(document, "Personal tax account.")
    }

    "show the expected content for the notify change of details link" in {
      val link = document.select("a[id*='notifyChangeOfDetailsLink']").asScala

      link.exists(e => e.attribute("href").getValue == "/notify-changes-of-details") mustBe true
      link.exists(e => e.text contains "if your circumstances change (opens in new tab)") mustBe true
    }

    "show the expected Welsh content" in {
      welshDocument.select("h1").text mustBe "Deall eich cyfrif ar-lein CThEF"
      welshDocument.select("h2").first().text mustBe "Sut i ddefnyddio’ch cyfrif ar-lein CThEF"
      welshDocument.select("h3").asScala.map(_.text).toSeq mustBe Seq(
        "Eich tasgau",
        "Gweithgarwch diweddar",
        "Trethi a budd-daliadau",
        "Newyddion CThEF",
        "Cymorth CThEF"
      )

      assertContainsText(
        welshDocument,
        "Mae eich cyfrif ar-lein CThEF yn rhoi mynediad at y gwasanaethau canlynol:"
      )
      assertContainsText(
        welshDocument,
        "Gallwch ddefnyddio eich cyfrif drwy ddewis y gwahanol adrannau ar eich tudalen ‘Hafan y Cyfrif’."
      )
      assertContainsText(
        welshDocument,
        "Cael mynediad at dasgau y mae’n rhaid eu cwblhau, fel hawlio ad-daliadau neu dalu biliau treth."
      )
      assertContainsText(
        welshDocument,
        "Dim ond eich tasgau ar gyfer y gwasanaethau canlynol y mae’ch cyfrif ar-lein CThEF yn dangos:"
      )
      assertContainsText(welshDocument, "Efallai fod gennych dasgau eraill:")
      assertContainsText(
        welshDocument,
        "Gweld y diweddaraf, megis taliadau o’ch swydd neu newidiadau i’ch cod treth."
      )
      assertContainsText(
        welshDocument,
        "Gwirio’r trethi a’r budd-daliadau sydd gennych ar hyn o bryd a dysgwch am eraill a allai fod yn berthnasol i chi."
      )
      assertContainsText(welshDocument, "Cael y diweddaraf gan CThEF.")
      assertContainsText(welshDocument, "Cael cymorth technegol a cymorth gyda threthi a budd-daliadau.")
    }

    "show the expected Welsh list content and order" in {
      welshDocument.select("#accessList li").eachText().asScala.toSeq mustBe Seq(
        "Talu Wrth Ennill (TWE)",
        "Hunanasesiad",
        "Budd-dal Plant",
        "Lwfans Priodasol",
        "Yswiriant Gwladol a Phensiwn y Wladwriaeth",
        "Crynodeb Treth Blynyddol"
      )
      welshDocument.select("#taskList li").eachText().asScala.toSeq mustBe Seq(
        "Talu Wrth Ennill (TWE)",
        "Hunanasesiad",
        "Budd-dal Plant",
        "Yswiriant Gwladol a Phensiwn y Wladwriaeth"
      )
      welshDocument.select("#otherList li").eachText().asScala.toSeq mustBe Seq(
        "os ydych yn defnyddio gwasanaethau eraill gan CThEF (efallai y byddwch yn defnyddio gwahanol gyfrifon Porth y Llywodraeth ar eu cyfer)",
        "os bydd eich amgylchiadau’n newid (yn agor tab newydd)",
        "os oes gennych gyfrif treth busnes"
      )
    }

    "show the expected Welsh notify change of details link" in {
      val link = welshDocument.select("a[id*='notifyChangeOfDetailsLink']").asScala

      link.exists(e => e.attribute("href").getValue == "/notify-changes-of-details") mustBe true
      link.exists(e => e.text contains "os bydd eich amgylchiadau’n newid (yn agor tab newydd)") mustBe true
    }

    "not render placeholder content" in {
      assertNotContainText(document, "TBC")
      assertNotContainText(welshDocument, "TBC")
    }

    "show the expected content for the back link" in {
      val link = document.select("a[id*='menu.back']").asScala

      link.exists(e => e.attribute("href").getValue == "#") mustBe true
      link.exists(e => e.text contains "Back") mustBe true
    }
  }
}
