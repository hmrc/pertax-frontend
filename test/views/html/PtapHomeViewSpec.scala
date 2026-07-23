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
import models.admin.PtapActivityTabToggle
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import repositories.JourneyCacheRepository
import testUtils.HmrcCardModelFixtures
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import viewmodels.{CardContainerModel, PtapAlertBanner, PtapHomeViewModel, SecondaryNavModel, TabModel}
import viewmodels.TabEnum.*

import scala.concurrent.Future

class PtapHomeViewSpec extends ViewSpec {

  lazy val home: PtapHomeView                       = inject[PtapHomeView]
  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  val taskCompletedMessage      = "It can take up to 10 days for completed tasks to be removed from the list."
  val welshTaskCompletedMessage =
    "Gall gymryd hyd at 10 diwrnod i’r tasgau sydd wedi’u cwblhau gael eu tynnu o’r rhestr."

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

  private val defaultSecondaryNav = SecondaryNavModel(
    classes = Some("govuk-!-margin-bottom-6"),
    items = Seq(
      TabModel(text = "Your tasks", href = Task.href(Some("?ptap=true")), notificationCount = Some(2)),
      TabModel(text = "Taxes and benefits", href = Tax.href(Some("?ptap=true")), current = true),
      TabModel(text = "HMRC news", href = News.href(Some("?ptap=true"))),
      TabModel(text = "Support", href = Support.href(Some("?ptap=true")))
    )
  )

  private def taskTabContent(
    cards: Seq[models.HmrcCardModel] = Seq.empty
  ): CardContainerModel =
    CardContainerModel(
      defaultInset = Task.defaultInset(Some("true")),
      cards = cards,
      cardHeadingLevel = "h2",
      listAriaLabel = Some("Your tasks")
    )

  private def activityTabContent(
    cards: Seq[models.HmrcCardModel] = Seq.empty
  ): CardContainerModel =
    CardContainerModel(
      defaultInset = Activity.defaultInset(Some("true")),
      cards = cards,
      cardHeadingLevel = "h2",
      listAriaLabel = Some("Recent activity")
    )

  val homeViewModel: PtapHomeViewModel =
    PtapHomeViewModel(
      showUserResearchBanner = true,
      saUtr = None,
      breathingSpaceIndicator = true,
      alertBannerContent = None,
      name = None,
      secondaryNav = defaultSecondaryNav,
      tabContent = List(taskTabContent())
    )

  "Rendering PtapHomeView.scala.html" must {

    "show the users name and not 'Your account' when the user has details and is not a GG user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      lazy val document: Document =
        asDocument(
          home(
            homeViewModel.copy(name = Some("Firstname Lastname"))
          ).toString
        )

      document.select("header.hmrc-page-heading h1").text mustBe "Personal tax account"
      document.select("header.hmrc-page-heading .govuk-caption-xl").text mustBe "Firstname Lastname"
    }

    "show the users name and not 'Your account' when the user has no details but is a GG user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      lazy val document: Document =
        asDocument(
          home(
            homeViewModel.copy(name = Some("Firstname Lastname"))
          ).toString
        )

      document.select("header.hmrc-page-heading h1").text mustBe "Personal tax account"
      document.select("header.hmrc-page-heading .govuk-caption-xl").text mustBe "Firstname Lastname"
    }

    "show 'Your account' and not the users name when the user has no details and is not a GG user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      lazy val document: Document =
        asDocument(home(homeViewModel).toString)

      document.select("header.hmrc-page-heading h1").text mustBe "Personal tax account"
      document.select("header.hmrc-page-heading .govuk-caption-xl").text mustBe "Your account"
    }

    "must not show the UTR if the user is not a self assessment user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

      val view = home(homeViewModel).toString

      view must not contain messages("label.home_page.utr")
    }

    "must show the UTR if the user is a self assessment user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val utr                                                       = new SaUtrGenerator().nextSaUtr.utr
      val view                                                      = home(homeViewModel.copy(saUtr = Some(utr))).toString

      view must include(messages("label.home_page.utr"))
      view must include(utr)
    }

    "show the alert banner if there is some alert content" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      val viewModelWithAlert =
        homeViewModel.copy(
          alertBannerContent = Some(PtapAlertBanner(Html("something to alert")))
        )

      val view = Jsoup.parse(home(viewModelWithAlert).toString)

      view.getElementById("alert-banner") must not be null
      view.toString                       must include("something to alert")
    }

    "not show the alert banner if no alert content" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val view                                                      = Jsoup.parse(home(homeViewModel).toString)

      view.getElementById("alert-banner") mustBe null
    }

    "must render SecondaryNav with the correct tab content" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val doc: Document                                             = asDocument(home(homeViewModel).toString())

      doc.select("nav.x-govuk-secondary-navigation").size mustBe 1
      doc.select("ul.x-govuk-secondary-navigation__list").size mustBe 1
      doc.select("a.x-govuk-secondary-navigation__link").size mustBe 4

      doc.select("a[href=/personal-account].x-govuk-secondary-navigation__link")                    must not be null
      doc.select("a[href=/personal-account/taxes-and-benefits].x-govuk-secondary-navigation__link") must not be null
      doc.select("a[href=/personal-account/hmrc-news].x-govuk-secondary-navigation__link")          must not be null
      doc.select("a[href=/personal-account/support].x-govuk-secondary-navigation__link")            must not be null
    }

    "not render a duplicate heading for Task tab content" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val doc                                                       = asDocument(home(homeViewModel).toString)
      doc.getElementById("tab-content-header") mustBe null
    }

    "render task cards from fixtures when provided" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val viewModel                                                 = homeViewModel.copy(tabContent = List(taskTabContent(HmrcCardModelFixtures.taskCards)))
      val doc                                                       = asDocument(home(viewModel).toString)
      doc.select(".hmrc-card").size() mustBe 2
      doc.select("h2.hmrc-card__heading").size() mustBe 2
      doc.select(".hmrc-card__heading").text() must include("You owe tax for 2023-24")
      doc.select(".hmrc-card__heading").text() must include("HMRC owes you a refund for 2022-23")
    }

    "render the task count badge on the tasks tab nav item" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val doc                                                       = asDocument(home(homeViewModel).toString)
      doc.select(".hmrc-notification-badge").text() mustBe "2"
    }

    "render Activity cards without a duplicate visible heading" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      when(mockFeatureFlagService.get(PtapActivityTabToggle))
        .thenReturn(Future.successful(FeatureFlag(PtapActivityTabToggle, isEnabled = true)))
      val activityNav                                               = defaultSecondaryNav.copy(
        items = defaultSecondaryNav.items.map(i => i.copy(current = i.href == Activity.href()))
      )
      val doc                                                       = asDocument(
        home(
          homeViewModel.copy(
            secondaryNav = activityNav,
            tabContent = List(activityTabContent(HmrcCardModelFixtures.activityCards))
          )
        ).toString
      )
      doc.getElementById("tab-content-header") mustBe null
      doc.select("ul.hmrc-card__container").attr("aria-label") mustBe "Recent activity"
      doc.select(".hmrc-card").size() mustBe 2
      doc.select("h2.hmrc-card__heading").size() mustBe 2
      doc.select(".hmrc-card__heading").text() must include("Tax code change")
    }

    "render a list of accessible card containers without duplicate headings" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val viewModel                                                 = homeViewModel.copy(
        tabContent = List(
          taskTabContent(HmrcCardModelFixtures.taskCards),
          activityTabContent(HmrcCardModelFixtures.activityCards)
        )
      )
      val doc                                                       = asDocument(home(viewModel).toString)
      val containers                                                = doc.select("ul.hmrc-card__container")
      containers.size() mustBe 2
      doc
        .select(".hmrc-card")
        .size() mustBe HmrcCardModelFixtures.taskCards.size + HmrcCardModelFixtures.activityCards.size
      containers.get(0).attr("aria-label") mustBe "Your tasks"
      containers.get(1).attr("aria-label") mustBe "Recent activity"
      doc.select(".hmrc-card__heading").text() must include("You owe tax for 2023-24")
      doc.select(".hmrc-card__heading").text() must include("Tax code change")
    }

    "render breathing space indicator when enabled" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val doc                                                       = asDocument(home(homeViewModel).toString)
      doc.text() must include("BREATHING SPACE")
    }

    "not render breathing space indicator when disabled" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val doc                                                       = asDocument(home(homeViewModel.copy(breathingSpaceIndicator = false)).toString)
      doc.text() must not include "BREATHING SPACE"
    }
    "show the default inset text when the Your tasks tab is selected." in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val doc                                                       = asDocument(home(homeViewModel).toString)

      val placeholder_text = doc
        .select("div.govuk-grid-column-two-thirds")
        .select("div.govuk-inset-text")
      placeholder_text.size mustBe 1
      placeholder_text.select("p.govuk-body").size mustBe 2

      val first_line  = placeholder_text.select("p.govuk-body").asList().get(0)
      val second_line = placeholder_text.select("p.govuk-body").asList().get(1)
      first_line.text() mustBe "This page shows refunds and tax you owe."
      second_line
        .text() mustBe "Check Taxes and benefits for anything else you need to do."
      second_line.select(s"a.govuk-link").text() mustBe "Taxes and benefits"
    }
    "show the default inset text when the Recent activity tab is selected." in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val activityNav                                               = defaultSecondaryNav.copy(
        items = defaultSecondaryNav.items.map(i => i.copy(current = i.href == Activity.href()))
      )
      val viewModel                                                 = homeViewModel.copy(
        secondaryNav = activityNav,
        tabContent = List(activityTabContent(HmrcCardModelFixtures.activityCards))
      )
      val doc                                                       = asDocument(home(viewModel).toString)
      val placeholder_text                                          = doc
        .select("div.govuk-grid-column-two-thirds")
        .select("div.govuk-inset-text")
      placeholder_text.size mustBe 1
      placeholder_text.select("p.govuk-body").size mustBe 2
      val first_line                                                = placeholder_text.select("p.govuk-body").asList().get(0)
      val second_line                                               = placeholder_text.select("p.govuk-body").asList().get(1)
      first_line.text() mustBe "This page shows your recent activity."
      second_line
        .text() mustBe "Check Your tasks for anything you need to do."
      second_line.select(s"a.govuk-link").text() mustBe "Your tasks"
    }
    "show the default inset text when the Your tasks tab is selected and there are no cards to load." in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val viewModel                                                 = homeViewModel.copy(tabContent = List(taskTabContent()))
      val doc                                                       = asDocument(home(viewModel).toString)

      val placeholder_text = doc
        .select("div.govuk-grid-column-two-thirds")
        .select("div.govuk-inset-text")
      placeholder_text.size mustBe 1
      placeholder_text.select("p.govuk-body").size mustBe 2

      val first_line  = placeholder_text.select("p.govuk-body").asList().get(0)
      val second_line = placeholder_text.select("p.govuk-body").asList().get(1)
      first_line.text() mustBe "This page shows refunds and tax you owe."
      second_line
        .text() mustBe "Check Taxes and benefits for anything else you need to do."
      second_line.select(s"a.govuk-link").text() mustBe "Taxes and benefits"
    }
    "show the default inset text when the Recent activity tab is selected and there are no cards to load." in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

      when(mockFeatureFlagService.get(PtapActivityTabToggle))
        .thenReturn(Future.successful(FeatureFlag(PtapActivityTabToggle, isEnabled = true)))

      val activityNav      = defaultSecondaryNav.copy(
        items = defaultSecondaryNav.items.map(i => i.copy(current = i.href == Activity.href()))
      )
      val viewModel        = homeViewModel.copy(secondaryNav = activityNav, tabContent = List(activityTabContent()))
      val doc              = asDocument(home(viewModel).toString)
      val placeholder_text = doc
        .select("div.govuk-grid-column-two-thirds")
        .select("div.govuk-inset-text")
      placeholder_text.size mustBe 1
      placeholder_text.select("p.govuk-body").size mustBe 2
      val first_line       = placeholder_text.select("p.govuk-body").asList().get(0)
      val second_line      = placeholder_text.select("p.govuk-body").asList().get(1)
      first_line.text() mustBe "This page shows your recent activity."
      second_line
        .text() mustBe "Check Your tasks for anything you need to do."
      second_line.select(s"a.govuk-link").text() mustBe "Your tasks"
    }

    "render task completed message when enabled" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      val viewModel = homeViewModel.copy(
        showTaskCompletedMessage = true,
        tabContent = List(taskTabContent(HmrcCardModelFixtures.taskCards))
      )
      val doc       = asDocument(home(viewModel).toString)
      doc.text() must include(taskCompletedMessage)
    }

    "not render task completed message when disabled" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      val viewModel = homeViewModel.copy(
        showTaskCompletedMessage = false,
        tabContent = List(taskTabContent(HmrcCardModelFixtures.taskCards))
      )
      val doc       = asDocument(home(viewModel).toString)
      doc.text() must not include taskCompletedMessage
    }

    "render task completed message in Welsh" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      val viewModel = homeViewModel.copy(
        showTaskCompletedMessage = true,
        tabContent = List(taskTabContent(HmrcCardModelFixtures.taskCards))
      )
      val doc       = asDocument(home(viewModel)(userRequest, welshMessages).toString)
      doc.text() must include(welshTaskCompletedMessage)
    }
  }
}
