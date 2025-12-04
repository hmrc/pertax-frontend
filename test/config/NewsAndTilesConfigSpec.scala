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

package config

import controllers.auth.requests.UserRequest
import models.NewsAndContentModel
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.AnyContentAsEmpty
import testUtils.BaseSpec
import testUtils.UserRequestFixture.buildUserRequest
import play.api.test.FakeRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NewsAndTilesConfigSpec extends BaseSpec {

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  val formatter: DateTimeFormatter                              = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  "getNewsAndContentModelList" must {
    "read configuration and create a list ordered by recency, truncating after max items reached" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.news.max"                                  -> 3,
          "feature.news.override-start-and-end-dates.enabled" -> false,
          "feature.news.items.0.name"                         -> "childBenefits",
          "feature.news.items.0.start-date"                   -> LocalDate.now().format(formatter),
          "feature.news.items.0.enddate"                      -> LocalDate.now().plusYears(1).format(formatter),
          "feature.news.items.0.dynamic-content"              -> true,
          "feature.news.items.1.name"                         -> "hmrcApp",
          "feature.news.items.1.start-date"                   -> LocalDate.now().minusWeeks(2).format(formatter),
          "feature.news.items.1.dynamic-content"              -> true,
          "feature.news.items.2.name"                         -> "payeEmployments",
          "feature.news.items.2.start-date"                   -> LocalDate.now().minusWeeks(1).format(formatter),
          "feature.news.items.2.end-date"                     -> LocalDate.now().plusYears(1).format(formatter),
          "feature.news.items.2.dynamic-content"              -> true,
          "feature.news.items.3.name"                         -> "third",
          "feature.news.items.3.start-date"                   -> LocalDate.now().minusWeeks(1).format(formatter),
          "feature.news.items.3.end-date"                     -> LocalDate.now().plusYears(1).format(formatter),
          "feature.news.items.3.dynamic-content"              -> true,
          "play.cache.bindCaches"                             -> List("controller-cache", "document-cache"),
          "play.cache.createBoundCaches"                      -> false
        )
        .build()

      val sut: NewsAndTilesConfig = app.injector.instanceOf[NewsAndTilesConfig]

      sut.getNewsAndContentModelList() mustBe List(
        NewsAndContentModel(
          "childBenefits",
          "",
          "",
          isDynamic = true,
          LocalDate.now(),
          true
        ),
        NewsAndContentModel(
          "payeEmployments",
          "",
          "",
          isDynamic = true,
          LocalDate.now().minusWeeks(1),
          true
        ),
        NewsAndContentModel(
          "hmrcApp",
          "",
          "",
          isDynamic = true,
          LocalDate.now().minusWeeks(2),
          true
        )
      )
    }

    "read configuration and create a list without expired entries" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.news.max"                                  -> 3,
          "feature.news.override-start-and-end-dates.enabled" -> false,
          "feature.news.items.0.name"                         -> "childBenefits",
          "feature.news.items.0.start-date"                   -> LocalDate.now().format(formatter),
          "feature.news.items.0.end-date"                     -> LocalDate.now().minusDays(1).format(formatter),
          "feature.news.items.0.dynamic-content"              -> true,
          "feature.news.items.1.name"                         -> "hmrcApp",
          "feature.news.items.1.start-date"                   -> LocalDate.now().minusWeeks(2).format(formatter),
          "feature.news.items.1.dynamic-content"              -> true,
          "feature.news.items.2.name"                         -> "payeEmployments",
          "feature.news.items.2.start-date"                   -> LocalDate.now().minusWeeks(1).format(formatter),
          "feature.news.items.2.end-date"                     -> LocalDate.now().minusDays(1).format(formatter),
          "feature.news.items.2.dynamic-content"              -> true,
          "play.cache.bindCaches"                             -> List("controller-cache", "document-cache"),
          "play.cache.createBoundCaches"                      -> false
        )
        .build()

      val sut = app.injector.instanceOf[NewsAndTilesConfig]

      sut.getNewsAndContentModelList() mustBe List(
        NewsAndContentModel(
          "hmrcApp",
          "",
          "",
          isDynamic = true,
          LocalDate.now().minusWeeks(2),
          true
        )
      )
    }
    "read configuration and create a list with expired entries or not started entries if overrideStartAndEndDatesForNewsItemsEnabled is true" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.news.max"                                  -> 3,
          "feature.news.override-start-and-end-dates.enabled" -> true,
          "feature.news.items.0.name"                         -> "childBenefits",
          "feature.news.items.0.start-date"                   -> LocalDate.now().format(formatter),
          "feature.news.items.0.end-date"                     -> LocalDate.now().minusDays(1).format(formatter),
          "feature.news.items.0.dynamic-content"              -> true,
          "feature.news.items.1.name"                         -> "hmrcApp",
          "feature.news.items.1.start-date"                   -> LocalDate.now().minusWeeks(2).format(formatter),
          "feature.news.items.1.dynamic-content"              -> true,
          "feature.news.items.2.name"                         -> "payeEmployments",
          "feature.news.items.2.start-date"                   -> LocalDate.now().minusWeeks(1).format(formatter),
          "feature.news.items.2.end-date"                     -> LocalDate.now().minusDays(1).format(formatter),
          "feature.news.items.2.dynamic-content"              -> true,
          "play.cache.bindCaches"                             -> List("controller-cache", "document-cache"),
          "play.cache.createBoundCaches"                      -> false
        )
        .build()

      val sut = app.injector.instanceOf[NewsAndTilesConfig]

      sut.getNewsAndContentModelList().length mustBe 3

    }

    "read configuration and create an empty list if all entries have expired" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.news.max"                                  -> 3,
          "feature.news.override-start-and-end-dates.enabled" -> false,
          "feature.news.items.0.name"                         -> "childBenefits",
          "feature.news.items.0.start-date"                   -> LocalDate.now().format(formatter),
          "feature.news.items.0.end-date"                     -> LocalDate.now().minusDays(1).format(formatter),
          "feature.news.items.0.dynamic-content"              -> true,
          "feature.news.items.1.name"                         -> "hmrcApp",
          "feature.news.items.1.start-date"                   -> LocalDate.now().minusWeeks(2).format(formatter),
          "feature.news.items.1.end-date"                     -> LocalDate.now().minusDays(1).format(formatter),
          "feature.news.items.1.dynamic-content"              -> true,
          "feature.news.items.2.name"                         -> "payeEmployments",
          "feature.news.items.2.start-date"                   -> LocalDate.now().minusWeeks(1).format(formatter),
          "feature.news.items.2.end-date"                     -> LocalDate.now().minusDays(1).format(formatter),
          "feature.news.items.2.dynamic-content"              -> true,
          "play.cache.bindCaches"                             -> List("controller-cache", "document-cache"),
          "play.cache.createBoundCaches"                      -> false
        )
        .build()

      val sut = app.injector.instanceOf[NewsAndTilesConfig]

      sut.getNewsAndContentModelList() mustBe List.empty[NewsAndContentModel]
    }
    "read configuration and create a list without items requiring a certain enrolment the user does not have" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.news.max"                                  -> 3,
          "feature.news.override-start-and-end-dates.enabled" -> true,
          "feature.news.items.0.name"                         -> "childBenefits",
          "feature.news.items.0.start-date"                   -> LocalDate.now().format(formatter),
          "feature.news.items.0.end-date"                     -> LocalDate.now().minusDays(1).format(formatter),
          "feature.news.items.0.dynamic-content"              -> true,
          "feature.news.items.1.name"                         -> "hmrcApp",
          "feature.news.items.1.start-date"                   -> LocalDate.now().minusWeeks(2).format(formatter),
          "feature.news.items.1.dynamic-content"              -> true,
          "feature.news.items.1.enrolment"                    -> "IR-SA",
          "feature.news.items.2.name"                         -> "payeEmployments",
          "feature.news.items.2.start-date"                   -> LocalDate.now().minusWeeks(1).format(formatter),
          "feature.news.items.2.end-date"                     -> LocalDate.now().minusDays(1).format(formatter),
          "feature.news.items.2.dynamic-content"              -> true,
          "feature.news.items.2.enrolment"                    -> "Unassigned",
          "play.cache.bindCaches"                             -> List("controller-cache", "document-cache"),
          "play.cache.createBoundCaches"                      -> false
        )
        .build()

      val sut = app.injector.instanceOf[NewsAndTilesConfig]

      sut.getNewsAndContentModelList().length mustBe 2

    }
  }
}
