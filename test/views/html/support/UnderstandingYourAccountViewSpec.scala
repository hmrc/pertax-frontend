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

  lazy val home: UnderstandingYourAccountView = inject[UnderstandingYourAccountView]

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
    when(mockConfigDecorator.personalAccount).thenReturn("/personal-account")
    when(mockConfigDecorator.notifyChangeOfDetails).thenReturn("/notify-changes-of-details")
    when(mockConfigDecorator.getFeedbackSurveyUrl(any())).thenReturn("/feedback/url")
  }

  "Rendering UnderstandingYourAccountView.scala.html" must {
    lazy val document: Document = asDocument(home().toString)

    "show the expected headers for Understanding your HMRC Online account page" in {
      document.select("h1").asScala.exists(e => e.text == "Understanding your HMRC Online account") mustBe true
      document.select("h2").asScala.exists(e => e.text == "How to use your HMRC Online account") mustBe true

      val h3s = document.select("h3").asScala
      h3s.exists(e => e.text == "Your tasks")
      h3s.exists(e => e.text == "Recent activity")
      h3s.exists(e => e.text == "Taxes and benefits")
      h3s.exists(e => e.text == "HMRC support")
    }

    "show the expected paragraphs" in {
      val paragraphs = document.select("p").asScala

      paragraphs.exists(e =>
        e.text contains "Use your HMRC Online account by selecting the following sections within your"
      ) mustBe true
      paragraphs.exists(e => e.text contains "Personal tax account.") mustBe true

      paragraphs.exists(e =>
        e.text contains "Complete tasks, such as claiming a refund or paying a tax bill."
      ) mustBe true
      paragraphs.exists(e => e.text contains "You’ll only see tasks for:") mustBe true
      paragraphs.exists(e => e.text contains "You may have other tasks if:") mustBe true
      paragraphs.exists(e =>
        e.text contains "View recent updates such as payments from your job or tax code changes."
      ) mustBe true
      paragraphs.exists(e =>
        e.text contains "Check the taxes and benefits you currently have and find out about others that may be relevant to you."
      ) mustBe true
      paragraphs.exists(e => e.text contains "Get technical support and help with taxes and benefits.") mustBe true
    }

    "show the expected content for the access list" in {
      val list = document.select("ul[id*='accessList']").select("li").asScala
      list.size mustBe 6

      list.exists(e => e.text contains "Pay As You Earn (PAYE)") mustBe true
      list.exists(e => e.text contains "Self Assessment") mustBe true
      list.exists(e => e.text contains "National Insurance and State Pension") mustBe true
      list.exists(e => e.text contains "Annual Tax Summary") mustBe true
      list.exists(e => e.text contains "Child Benefit") mustBe true
      list.exists(e => e.text contains "Marriage Allowance") mustBe true
    }

    "show the expected content for the task list" in {
      val list = document.select("ul[id*='taskList']").select("li").asScala
      list.size mustBe 4

      list.exists(e => e.text contains "Pay As You Earn (PAYE)") mustBe true
      list.exists(e => e.text contains "Self Assessment") mustBe true
      list.exists(e => e.text contains "National Insurance and State Pension") mustBe true
      list.exists(e => e.text contains "Child Benefit") mustBe true
    }

    "show the expected content for the other list" in {
      val list = document.select("ul[id*='otherList']").select("li").asScala
      list.size mustBe 3

      list.exists(e => e.text contains "you use other HMRC services") mustBe true
      list.exists(e => e.text contains "your circumstances change (opens in new tab)") mustBe true
      list.exists(e => e.text contains "you have a Business Tax Account") mustBe true
    }

    "show the expected content for the personal account link" in {
      val link = document.select("a[id*='personalAccountLink']").asScala

      link.exists(e => e.attribute("href").getValue == "/personal-account") mustBe true
      link.exists(e => e.text contains "Personal tax account.") mustBe true
    }

    "show the expected content for the notify change of details link" in {
      val link = document.select("a[id*='notifyChangeOfDetailsLink']").asScala

      link.exists(e => e.attribute("href").getValue == "/notify-changes-of-details") mustBe true
      link.exists(e => e.text contains "your circumstances change (opens in new tab)") mustBe true
    }
  }
}
