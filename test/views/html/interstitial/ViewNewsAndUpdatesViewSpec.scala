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

package views.html.interstitial

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.NewsAndContentModel
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

import java.time.LocalDate

class ViewNewsAndUpdatesViewSpec extends ViewSpec {

  lazy val viewNewsAndUpdatesView: ViewNewsAndUpdatesView       = inject[ViewNewsAndUpdatesView]
  lazy implicit val configDecorator: ConfigDecorator            = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  "Rendering ViewNewsAndUpdatesView.scala.html" must {

    val firstNewsAndContentModel = NewsAndContentModel(
      "nicSection",
      "1.25 percentage points uplift in National Insurance contributions (base64 encoded)",
      "<p>base64 encoded content with html example 1</p>",
      isDynamic = false,
      LocalDate.now,
      true
    )

    val secondNewsAndContentModel = NewsAndContentModel(
      "exampleSection",
      "This is an example News item",
      "<p>base64 encoded content with html example 2</p>",
      isDynamic = false,
      LocalDate.now,
      true
    )

    "show the select news item content and headlines for others" in {
      val doc = asDocument(
        viewNewsAndUpdatesView(
          List[NewsAndContentModel](firstNewsAndContentModel, secondNewsAndContentModel),
          "nicSection"
        ).toString
      )

      val doc2 =
        asDocument(
          viewNewsAndUpdatesView(
            List[NewsAndContentModel](firstNewsAndContentModel, secondNewsAndContentModel),
            "exampleSection"
          ).toString
        )

      doc.getElementById("newsHeading").text() must include(firstNewsAndContentModel.shortDescription)
      doc.html()                               must include(firstNewsAndContentModel.content)
      doc.text()                               must include(messages("label.other_news_and_updates"))
      doc.text()                               must include(secondNewsAndContentModel.shortDescription)
      doc.html() mustNot include(secondNewsAndContentModel.content)

      doc2.getElementById("newsHeading").text() must include(secondNewsAndContentModel.shortDescription)
      doc2.html()                               must include(secondNewsAndContentModel.content)
      doc2.text()                               must include(messages("label.other_news_and_updates"))
      doc2.text()                               must include(firstNewsAndContentModel.shortDescription)
      doc2.html() mustNot include(firstNewsAndContentModel.content)
    }

    "show the select news item content adn not Other new and updates heading" in {

      val doc =
        asDocument(
          viewNewsAndUpdatesView(
            List[NewsAndContentModel](firstNewsAndContentModel),
            "exampleSection"
          ).toString
        )

      doc.getElementById("newsHeading").text() must include(firstNewsAndContentModel.shortDescription)
      doc.html()                               must include(firstNewsAndContentModel.content)
      doc.text() mustNot include(messages("label.other_news_and_updates"))
    }
  }
}
