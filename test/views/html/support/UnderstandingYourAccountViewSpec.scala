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
    lazy val document: Document = asDocument(page().toString)

    "show the expected title and content for Understanding your personal tax account page" in {
      val title = document.select("h1").asScala

      title.exists(e => e.text contains "Understanding your personal tax account") mustBe true

    }

    "show the expected Welsh title and intro content for Understanding your personal tax account page" in {
      val welshDocument = asDocument(page()(userRequest, welshMessages).toString)
      val expectedTitle = "Deall eich Cyfrif Treth Personol"
      val expectedIntro =
        "Mae’ch Cyfrif Treth Personol yn caniatáu i chi reoli’ch trethi a’ch budd-daliadau mewn un lle."

      welshDocument.select("h1").text() mustBe expectedTitle
      welshDocument
        .select("div.govuk-grid-column-two-thirds")
        .select("p.govuk-body")
        .first()
        .text() mustBe expectedIntro
    }

    "show the expected list content  for the access list section" in {
      val list = document.select("ul[id*='accessList']").select("li").asScala

      list.exists(e => e.text contains "Taxes and benefits") mustBe true
      list.exists(e => e.text contains "Your tasks") mustBe true
      list.exists(e => e.text contains "HMRC news") mustBe true
      list.exists(e => e.text contains "Support") mustBe true
      list.size mustBe 4
    }

    "show the expected headers for Understanding your personal tax account page" in {
      val h2s = document.select("div.govuk-grid-column-two-thirds").select("h2").asScala
      h2s.exists(e => e.text == "Taxes and benefits")
      h2s.exists(e => e.text == "Your tasks")
      h2s.exists(e => e.text == "HMRC news")
      h2s.exists(e => e.text == "Support")
      h2s.size mustBe 4
    }

    "show the expected paragraphs for Understanding your personal account page" in {
      val paragraphs = document.select("div.govuk-grid-column-two-thirds").select("p.govuk-body").asScala

      paragraphs.exists(e =>
        e.text contains "Your personal tax account lets you manage your taxes and benefits in one place. You can use your account to access:"
      ) mustBe true
      paragraphs.exists(e =>
        e.text contains "Check the taxes and benefits you currently have and find others that may be relevant to you."
      ) mustBe true
      paragraphs.exists(e =>
        e.text contains "Complete tasks such as claiming a refund or paying tax you owe. This section only shows your Pay As You Earn (PAYE) related tasks."
      ) mustBe true
      paragraphs.exists(e => e.text contains "Read the latest updates from HMRC.") mustBe true
      paragraphs.exists(e => e.text contains "Get technical support and help with taxes and benefits.") mustBe true
      paragraphs.size mustBe 5
    }

    "show the expected content for the back link" in {
      val link = document.select("a[id*='menu.back']").asScala

      link.exists(e => e.attribute("href").getValue == "#") mustBe true
      link.exists(e => e.text contains "Back") mustBe true
    }
  }
}
