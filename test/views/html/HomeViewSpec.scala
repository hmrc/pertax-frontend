/*
 * Copyright 2023 HM Revenue & Customs
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.Mockito.{reset, when}
import play.api
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.domain.SaUtrGenerator
import viewmodels.HomeViewModel
import views.html.cards.home.PayAsYouEarnView
import controllers.bindable.Origin
import org.mockito.ArgumentMatchers.any
import repositories.JourneyCacheRepository

import scala.jdk.CollectionConverters._

class HomeViewSpec extends ViewSpec {

  lazy val home: HomeView             = inject[HomeView]
  lazy val payeCard: PayAsYouEarnView = inject[PayAsYouEarnView]

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
    when(mockConfigDecorator.getFeedbackSurveyUrl(any())).thenReturn("/feedback/url")

  }

  val homeViewModel: HomeViewModel =
    HomeViewModel(Nil, Nil, Nil, showUserResearchBanner = true, None, breathingSpaceIndicator = true, List.empty, None)

  "Rendering HomeView.scala.html" must {

    "show the users name and not 'Your account' when the user has details and is not a GG user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      lazy val document: Document =
        asDocument(home(homeViewModel.copy(name = Some("Firstname Lastname")), shutteringMessaging = false).toString)

      document.select("h1").asScala.exists(e => e.text == "Firstname Lastname") mustBe true
      document.select("h1").asScala.exists(e => e.text == "Your account") mustBe false
    }

    "show the users name and not 'Your account' when the user has no details but is a GG user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        request = FakeRequest()
      )

      lazy val document: Document =
        asDocument(home(homeViewModel.copy(name = Some("Firstname Lastname")), shutteringMessaging = false).toString)

      document.select("h1").asScala.exists(e => e.text == "Firstname Lastname") mustBe true
      document.select("h1").asScala.exists(e => e.text == "Your account") mustBe false
    }

    "show 'Your account' and not the users name when the user has no details and is not a GG user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())

      lazy val document: Document = asDocument(home(homeViewModel, shutteringMessaging = false).toString)

      document.select("h1").asScala.exists(e => e.text == "Your account") mustBe true
    }

    "must not show the UTR if the user is not a self assessment user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

      val view = home(homeViewModel, shutteringMessaging = false).toString

      view must not contain messages("label.home_page.utr")
    }

    "must show the UTR if the user is a self assessment user" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val utr                                                       = new SaUtrGenerator().nextSaUtr.utr
      val view                                                      = home(homeViewModel.copy(saUtr = Some(utr)), shutteringMessaging = false).toString

      view must include(messages("label.home_page.utr"))
      view must include(utr)
    }

    "show the Nps Shutter Banner when boolean is set to true" in {
      when(mockConfigDecorator.shutterBannerParagraphCy).thenReturn("Welsh content")
      when(mockConfigDecorator.shutterBannerParagraphEn).thenReturn(
        "A number of services will be unavailable from 10pm on Friday 12 July to 7am Monday 15 July."
      )

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val view                                                      = home(homeViewModel, shutteringMessaging = true).toString

      view must include(
        "A number of services will be unavailable from 10pm on Friday 12 July to 7am Monday 15 July."
      )
    }

    "not how the Nps Shutter Banner when boolean is set to false" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val view                                                      = home(homeViewModel, shutteringMessaging = false).toString

      view mustNot include(
        "A number of services will be unavailable from 10pm on Friday 12 July to 7am Monday 15 July."
      )
    }

    "show the alert banner if there is some alert content" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val view                                                      = Jsoup.parse(
        home(
          homeViewModel.copy(alertBannerContent = List(Html("something to alert"))),
          shutteringMessaging = true
        ).toString
      )

      view.getElementById("alert-banner") must not be null
      view.toString                       must include("something to alert")
    }

    "not show the alert banner if no alert content" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      val view                                                      = Jsoup.parse(home(homeViewModel, shutteringMessaging = true).toString)

      view.getElementById("alert-banner") mustBe null
    }

  }
}
