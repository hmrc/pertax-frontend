/*
 * Copyright 2022 HM Revenue & Customs
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

import models.NewsAndContentModel
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import testUtils.BaseSpec

import java.time.LocalDate

class NewsAndTilesConfigSpec extends BaseSpec {

  lazy val messagesApi = injected[MessagesApi]

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  "getNewsAndContentModelList" must {
    "read configuration and create a list ordered by recency" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.news.childBenefits.start-date"        -> "2022-11-14",
          "feature.news.childBenefits.end-date"          -> "2023-04-30",
          "feature.news.childBenefits.dynamic-content"   -> true,
          "feature.news.payeEmployments.start-date"      -> "2022-11-01",
          "feature.news.payeEmployments.end-date"        -> "2023-01-02",
          "feature.news.payeEmployments.dynamic-content" -> true,
          "feature.news.hmrcApp.start-date"              -> "2021-10-31",
          "feature.news.hmrcApp.dynamic-content"         -> true
        )
        .build()

      val sut = app.injector.instanceOf[NewsAndTilesConfig]

      sut.getNewsAndContentModelList mustBe List(
        NewsAndContentModel(
          "childBenefits",
          "",
          "",
          true,
          LocalDate.of(2022, 11, 14)
        ),
        NewsAndContentModel(
          "payeEmployments",
          "",
          "",
          true,
          LocalDate.of(2022, 11, 1)
        ),
        NewsAndContentModel(
          "hmrcApp",
          "",
          "",
          true,
          LocalDate.of(2021, 10, 31)
        )
      )
    }
  }
}
