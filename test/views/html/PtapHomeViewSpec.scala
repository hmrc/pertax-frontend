/*
 * Copyright 2026 HM Revenue & Customs
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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.bindable.Origin
import models.PtapHomePlaceholderCardData
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import repositories.JourneyCacheRepository
import services.TabContentService
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.domain.SaUtrGenerator
import viewmodels.{PtapAlertBanner, PtapHomeTab, PtapHomeViewModel}

import scala.jdk.CollectionConverters.*

class PtapHomeViewSpec extends ViewSpec {

  lazy val ptapHomeView: PtapHomeView               = inject[PtapHomeView]
  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  private val tabContentService = new TabContentService

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
    when(mockConfigDecorator.ptaNinoSaveUrl).thenReturn("/personal-account/national-insurance-number")
    when(mockConfigDecorator.getFeedbackSurveyUrl(any())).thenReturn("/feedback/url")
    when(mockConfigDecorator.shutterBannerParagraphCy).thenReturn("Welsh content")
    when(mockConfigDecorator.shutterBannerParagraphEn).thenReturn(
      "A number of services will be unavailable from 10pm on Friday 12 July to 7am Monday 15 July."
    )
    when(mockConfigDecorator.shutterBannerLinkTextCy).thenReturn("Welsh link")
    when(mockConfigDecorator.shutterBannerLinkTextEn).thenReturn("Find out more")
  }

  private def viewModel(currentTab: PtapHomeTab = PtapHomeTab.Task, taskCount: Int = 2): PtapHomeViewModel =
    PtapHomeViewModel(
      tasks = Seq.empty,
      newsAndUpdates = None,
      showUserResearchBanner = true,
      saUtr = None,
      breathingSpaceIndicator = false,
      alertBannerContent = None,
      name = None,
      currentTab = currentTab.key,
      secondaryNav = tabContentService.getSecondaryNavModel(currentTab, taskCount),
      tabContent = tabContentService.getTabContentModel(currentTab, taskCount)
    )

  private def render(model: PtapHomeViewModel): Document = {
    implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
      buildUserRequest(request = FakeRequest())

    asDocument(ptapHomeView(model).toString)
  }

  "Rendering PtapHomeView.scala.html" must {

    "show the users name and not 'Your account' when the user has details" in {
      val document = render(viewModel().copy(name = Some("Firstname Lastname")))

      document.select("h1").asScala.exists(_.text == "Firstname Lastname") mustBe true
      document.select("h1").asScala.exists(_.text == "Your account") mustBe false
    }

    "show 'Your account' when no name is available" in {
      val document = render(viewModel())

      document.select("h1").asScala.exists(_.text == "Your account") mustBe true
    }

    "must not show the UTR if the user is not a self assessment user" in {
      val view = render(viewModel()).toString

      view must not contain messages("label.home_page.utr")
    }

    "must show the UTR if the user is a self assessment user" in {
      val utr  = new SaUtrGenerator().nextSaUtr.utr
      val view = render(viewModel().copy(saUtr = Some(utr))).toString

      view must include(messages("label.home_page.utr"))
      view must include(utr)
    }

    "show the alert banner if there is some alert content" in {
      val document = render(
        viewModel().copy(alertBannerContent = Some(PtapAlertBanner(Html("something to alert"))))
      )

      document.getElementById("alert-banner") must not be null
      document.toString                       must include("something to alert")
    }

    "not show the alert banner if no alert content" in {
      val document = render(viewModel())

      document.getElementById("alert-banner") mustBe null
    }

    "render SecondaryNav with route-backed tabs and the task count badge" in {
      val document = render(viewModel(taskCount = 3))

      document.select("nav.x-govuk-secondary-navigation").size() mustBe 1
      document.select("nav.x-govuk-secondary-navigation").first().attr("aria-labelledby") mustBe "secondary-nav-label"
      document.select("a.x-govuk-secondary-navigation__link").size() mustBe 5

      PtapHomeTab.all.foreach { tab =>
        document.select(s"a[href='${controllers.routes.HomeController.homePageTab(tab.key).url}']").size() mustBe 1
      }

      document.select("li.x-govuk-secondary-navigation__list-item--current a").attr("href") mustBe
        controllers.routes.HomeController.homePageTab(PtapHomeTab.Task.key).url
      document.select(".x-govuk-secondary-navigation__badge").text() mustBe "3"
    }

    "render only the Tasks card content for the task tab" in {
      val document = render(viewModel(PtapHomeTab.Task))

      document.select("#ptap-tab-content").size() mustBe 1
      document.select("#tasks-heading").text() mustBe "Tasks"
      document.select("#activities-heading").size() mustBe 0

      PtapHomePlaceholderCardData.taskCards.foreach { card =>
        document.text() must include(card.heading.text)
        document.select(s"a[href='${card.heading.url.get}']").size() mustBe 1
      }

      PtapHomePlaceholderCardData.activityCards.foreach { card =>
        document.text() must not include card.heading.text
      }
    }

    "render only the Activities card content for the activity tab" in {
      val document = render(viewModel(PtapHomeTab.Activity))

      document.select("#ptap-tab-content").size() mustBe 1
      document.select("#tasks-heading").size() mustBe 0
      document.select("#activities-heading").text() mustBe "Activities"

      PtapHomePlaceholderCardData.activityCards.foreach { card =>
        document.text() must include(card.heading.text)
        document.select(s"a[href='${card.heading.url.get}']").size() mustBe 1
      }

      PtapHomePlaceholderCardData.taskCards.foreach { card =>
        document.text() must not include card.heading.text
      }
    }

    "render no PTAD-142 placeholder content for tabs owned by later stories" in {
      val document = render(viewModel(PtapHomeTab.Tax))

      document.select("#ptap-tab-content").size() mustBe 1
      document.select("#ptap-tab-content h2").size() mustBe 0
      document.select("#ptap-tab-content .hmrc-card").size() mustBe 0
    }
  }
}
