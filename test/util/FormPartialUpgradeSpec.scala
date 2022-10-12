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

package util

import org.jsoup.Jsoup
import play.twirl.api.Html
import testUtils.BaseSpec

//TODO: To be deleted. See DDCNL-6008
class FormPartialUpgradeSpec extends BaseSpec {

  "Calling FormPartialUpgrade" must {

    "return HTML with the heading classes added to an h2" in {
      val heading = Html("<h2>Heading</h2>")
      val result  = FormPartialUpgrade.upgrade(heading)
      result mustBe Html("<h2 class=\"govuk-heading-m\">Heading</h2>")
    }

    "return HTML with the bulleted list classes added to a list" in {
      val list   = Html("<ul><li>List</li></ul>")
      val result = FormPartialUpgrade.upgrade(list)
      result mustBe Html(
        Jsoup
          .parse("<ul class=\"govuk-list govuk-list--bullet\"><li>List</li></ul>")
          .select("body")
          .html
      )
    }

    "return HTML with the govuk-link class added to a link" in {
      val link   = Html("<a href=\"link\">Link</a>")
      val result = FormPartialUpgrade.upgrade(link)
      result mustBe Html("<a href=\"link\" class=\"govuk-link\">Link</a>")
    }

    "return HTML with the govuk classes added to all expected elements" in {
      val list   = Html("<h2>Heading</h2><ul><li><a href=\"link\">Link</a></li><li><a href=\"link\">Link2</a></li></ul>")
      val result = FormPartialUpgrade.upgrade(list)
      result mustBe
        Html(
          Jsoup
            .parse(
              "<h2 class=\"govuk-heading-m\">Heading</h2><ul class=\"govuk-list govuk-list--bullet\"><li><a href=\"link\" class=\"govuk-link\">Link</a></li><li><a href=\"link\" class=\"govuk-link\">Link2</a></li></ul>"
            )
            .select("body")
            .html
        )
    }
  }
}
