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

package views.html.integration

import config.ConfigDecorator
import views.html.ViewSpec

import java.time.{LocalDateTime, ZonedDateTime}

class MainContentHeaderSpec extends ViewSpec {

  implicit val configDecorator: ConfigDecorator = mock[ConfigDecorator]

  lazy val view = injected[MainContentHeaderView]

  "Rendering mainContentHeader.scala.html" must {

    "show last logged in details with name when a name is present and a lastLogin is supplied" in {
      val millis = ZonedDateTime.parse("1982-04-30T00:00:00.000+01:00").toLocalDateTime
      val document = asDocument(view(Some("Firstname"), Some(millis), Nil, false, None, None).toString)
      document.select(".last-login > p").text mustBe "Firstname, you last signed in 12:00am, Friday 30 April 1982"
    }

    "show last logged in details without name when no name is present and a lastLogin is supplied" in {
      val millis = ZonedDateTime.parse("1982-04-30T00:00:00.000+01:00").toLocalDateTime
      val document = asDocument(view(None, Some(millis), Nil, false, None, None).toString)
      document.select(".last-login > p").text mustBe "You last signed in 12:00am, Friday 30 April 1982"
    }

    "not show last logged in details when lastLogin is not supplied" in {
      val document = asDocument(view(None, None, Nil, false, None, None).toString)
      document.select(".last-login").isEmpty mustBe true
    }

    "show breadcrumb when one is passed" in {
      val document =
        asDocument(view(None, None, List(("/url", "Link Text"), ("/url2", "Link Text 2")), true, None, None).toString)
      val doc = asDocument(document.select("#global-breadcrumb").toString)

      doc.select("a").size() mustBe 2
      document.select("#global-breadcrumb").isEmpty mustBe false
    }

    "hide breadcrumb when none is passed" in {
      val document = asDocument(view(None, None, Nil, true, None, None).toString)
      document.select("#global-breadcrumb").isEmpty mustBe true
    }

    "show BETA banner showBetaBanner is true" in {
      val document = asDocument(view(None, None, Nil, true, None, None).toString)
      document.select(".beta-banner .phase-tag").text mustBe "BETA"
    }

    "hide BETA banner showBetaBanner is false" in {
      val document = asDocument(view(None, None, Nil, false, None, None).toString)
      document.select(".beta-banner .phase-tag").isEmpty mustBe true
    }

    "show feedback link in BETA banner when passed deskProToken with PTA" in {
      val document = asDocument(view(None, None, Nil, true, Some("PTA"), None).toString)
      document
        .select(".beta-banner .feedback")
        .text mustBe "This is a new service - your feedback will help us to improve it."
    }

    "hide feedback link in BETA banner when not passed any deskProToken" in {
      val document = asDocument(view(None, None, Nil, false, None, None).toString)
      document.select(".beta-banner .feedback").isEmpty mustBe true
    }

  }
}
