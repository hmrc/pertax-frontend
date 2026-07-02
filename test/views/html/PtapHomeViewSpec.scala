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
import viewmodels.{CardContainerModel, PtapAlertBanner, PtapHomeViewModel, SecondaryNavModel, TabEnum, TabModel}
import viewmodels.TabEnum.*

import scala.jdk.CollectionConverters.*

class PtapHomeViewSpec extends ViewSpec {

  lazy val home: PtapHomeView                       = inject[PtapHomeView]
  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

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
      TabModel(text = "Your tasks", href = Task.href(), current = true, notificationCount = Some(2)),
      TabModel(text = "Recent activity", href = Activity.href(), current = false),
      TabModel(text = "Taxes and benefits", href = Tax.href(), current = false),
      TabModel(text = "HMRC news", href = News.href(), current = false),
      TabModel(text = "Support", href = Support.href(), current = false)
    )
  )

  private def taskTabContent(
    cards: Seq[models.HmrcCardModel] = Seq.empty,
    headerId: String = "tab-content-header"
  ): CardContainerModel =
    CardContainerModel(
      emptyView = Html(""),
      header = Some("Your tasks"),
      cards = cards,
      headerId = Some(headerId)
    )

  private def activityTabContent(
    cards: Seq[models.HmrcCardModel],
    headerId: String = "tab-content-header"
  ): CardContainerModel =
    CardContainerModel(
      emptyView = Html(""),
      header = Some("Recent activity"),
      cards = cards,
      headerId = Some(headerId)
    )

  val homeViewModel: PtapHomeViewModel =
    PtapHomeViewModel(
      newsAndUpdates = None,
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
      doc.select("a.x-govuk-secondary-navigation__link").size mustBe 5

      doc.select("a[href=/personal-account].x-govuk-secondary-navigation__link")                    must not be null
      doc.select("a[href=/personal-account/recent-activity].x-govuk-secondary-navigation__link")    must not be null
      doc.select("a[href=/personal-account/taxes-and-benefits].x-govuk-secondary-navigation__link") must not be null
      doc.select("a[href=/personal-account/hmrc-news].x-govuk-secondary-navigation__link")          must not be null
      doc.select("a[href=/personal-account/support].x-govuk-secondary-navigation__link")            must not be null
    }

    "render tab content header with correct heading text" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val doc                                                       = asDocument(home(homeViewModel).toString)
      val header                                                    = doc.getElementById("tab-content-header")
      header must not be null
      header.text() mustBe "Your tasks"
    }

    "render task cards from fixtures when provided" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val viewModel                                                 = homeViewModel.copy(tabContent = List(taskTabContent(HmrcCardModelFixtures.taskCards)))
      val doc                                                       = asDocument(home(viewModel).toString)
      doc.select(".hmrc-card").size() mustBe 2
      doc.select(".hmrc-card__heading").text() must include("You owe tax for 2023-24")
      doc.select(".hmrc-card__heading").text() must include("HMRC owes you a refund for 2022-23")
    }

    "render an empty card container when no tab cards are provided" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val doc                                                       = asDocument(home(homeViewModel).toString)
      doc.select(".hmrc-card").size() mustBe 0
    }

    "render the task count badge on the tasks tab nav item" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val doc                                                       = asDocument(home(homeViewModel).toString)
      doc.select(".x-govuk-secondary-navigation__badge").text() mustBe "2"
    }

    "render the correct heading for the Activity tab with activity cards" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
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
      val header                                                    = doc.getElementById("tab-content-header")
      header                                   must not be null
      header.text() mustBe "Recent activity"
      doc.select(".hmrc-card").size() mustBe 2
      doc.select(".hmrc-card__heading").text() must include("Tax code change")
    }

    "render a list of card containers with corresponding headings and cards" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val viewModel                                                 = homeViewModel.copy(
        tabContent = List(
          taskTabContent(HmrcCardModelFixtures.taskCards, headerId = "tasks-content-header"),
          activityTabContent(HmrcCardModelFixtures.activityCards, headerId = "activity-content-header")
        )
      )
      val doc                                                       = asDocument(home(viewModel).toString)
      doc.select("ul.hmrc-card__container").size() mustBe 2
      doc
        .select(".hmrc-card")
        .size() mustBe HmrcCardModelFixtures.taskCards.size + HmrcCardModelFixtures.activityCards.size
      doc.getElementById("tasks-content-header").text() mustBe "Your tasks"
      doc.getElementById("activity-content-header").text() mustBe "Recent activity"
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
  }
}
