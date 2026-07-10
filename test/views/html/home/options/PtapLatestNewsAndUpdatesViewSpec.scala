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

package views.html.home.options

import config.{ConfigDecorator, NewsAndTilesConfig}
import controllers.auth.requests.UserRequest
import controllers.bindable.Origin
import models.NewsAndContentModel
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.JourneyCacheRepository
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

import java.time.LocalDate

class PtapLatestNewsAndUpdatesViewSpec extends ViewSpec {

  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]
  val mockNewsAndTilesConfig: NewsAndTilesConfig    = mock[NewsAndTilesConfig]

  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

  lazy val page: PtapLatestNewsAndUpdatesView = inject[PtapLatestNewsAndUpdatesView]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      api.inject.bind[ConfigDecorator].toInstance(mockConfigDecorator),
      api.inject.bind[NewsAndTilesConfig].toInstance(mockNewsAndTilesConfig),
      api.inject.bind[JourneyCacheRepository].toInstance(mock[JourneyCacheRepository])
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator, mockNewsAndTilesConfig)
    when(mockConfigDecorator.defaultOrigin).thenReturn(Origin("PERTAX"))
    when(mockConfigDecorator.personalAccount).thenReturn("/personal-account")
    when(mockConfigDecorator.getFeedbackSurveyUrl(any())).thenReturn("/feedback/url")
  }

  private val newsItem1 = NewsAndContentModel(
    newsSectionName = "checkYourPersonalDetails",
    shortDescription = "Check your personal details are up to date",
    content = "",
    isDynamic = false,
    startDate = LocalDate.now(),
    toBeDisplayed = true
  )

  private val newsItem2 = NewsAndContentModel(
    newsSectionName = "homepageUpdate",
    shortDescription = "Changes to the personal tax account homepage",
    content = "",
    isDynamic = false,
    startDate = LocalDate.now().minusDays(5),
    toBeDisplayed = true
  )

  "PtapLatestNewsAndUpdatesView" must {

    "render nothing when the news tile is disabled" in {
      when(mockConfigDecorator.isNewsAndUpdatesTileEnabled).thenReturn(false)
      when(mockNewsAndTilesConfig.getNewsAndContentModelList()(any(), any())).thenReturn(List(newsItem1))

      val document = asDocument(page().toString)
      document.select("div.hmrc-card").size() mustBe 0
      document.select("h2#hmrc-news-heading").size() mustBe 0
    }

    "render nothing when the news list is empty" in {
      when(mockConfigDecorator.isNewsAndUpdatesTileEnabled).thenReturn(true)
      when(mockNewsAndTilesConfig.getNewsAndContentModelList()(any(), any())).thenReturn(List.empty)

      val document = asDocument(page().toString)
      document.select("div.hmrc-card").size() mustBe 0
      document.select("h2#hmrc-news-heading").size() mustBe 0
    }

    "render the HMRC News heading when news items are present" in {
      when(mockConfigDecorator.isNewsAndUpdatesTileEnabled).thenReturn(true)
      when(mockNewsAndTilesConfig.getNewsAndContentModelList()(any(), any())).thenReturn(List(newsItem1))
      when(mockConfigDecorator.displayNewsAndUpdatesUrl(any())).thenReturn("/news/checkYourPersonalDetails")

      val document = asDocument(page().toString)
      document.select("h2#hmrc-news-heading").text() mustBe messages("label.hmrc_news")
    }

    "render one card per news item" in {
      when(mockConfigDecorator.isNewsAndUpdatesTileEnabled).thenReturn(true)
      when(mockNewsAndTilesConfig.getNewsAndContentModelList()(any(), any())).thenReturn(List(newsItem1, newsItem2))
      when(mockConfigDecorator.displayNewsAndUpdatesUrl(any())).thenReturn("/news/section")

      val document = asDocument(page().toString)
      document.select("div.hmrc-card").size() mustBe 2
    }

    "render each news item as a card with the correct link" in {
      when(mockConfigDecorator.isNewsAndUpdatesTileEnabled).thenReturn(true)
      when(mockNewsAndTilesConfig.getNewsAndContentModelList()(any(), any())).thenReturn(List(newsItem1, newsItem2))
      when(mockConfigDecorator.displayNewsAndUpdatesUrl("checkYourPersonalDetails"))
        .thenReturn("/news/checkYourPersonalDetails")
      when(mockConfigDecorator.displayNewsAndUpdatesUrl("homepageUpdate")).thenReturn("/news/homepageUpdate")

      val document = asDocument(page().toString)
      assertContainsLink(document, newsItem1.shortDescription, "/news/checkYourPersonalDetails")
      assertContainsLink(document, newsItem2.shortDescription, "/news/homepageUpdate")
    }

    "render the HMRC News heading in Welsh" in {
      when(mockConfigDecorator.isNewsAndUpdatesTileEnabled).thenReturn(true)
      when(mockNewsAndTilesConfig.getNewsAndContentModelList()(any(), any())).thenReturn(List(newsItem1))
      when(mockConfigDecorator.displayNewsAndUpdatesUrl(any())).thenReturn("/news/checkYourPersonalDetails")

      val welshDoc = asDocument(page()(welshMessages, userRequest).toString)
      welshDoc.select("h2#hmrc-news-heading").text() mustBe welshMessages("label.hmrc_news")
    }
  }
}
