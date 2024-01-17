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

package models

import play.api.libs.json.Json
import testUtils.BaseSpec

class SummaryCardPartialSpec extends BaseSpec {

  "Reads" must {
    "read valid json correctly" in {
      val json   = Json.obj(
        "partialName"    -> "card1",
        "partialContent" -> """\n  <div onclick=\"location.href='/tax-you-paid/2022-2023/paid-too-much';\" class=\"card active\"  data-journey-click=\"button - click:summary card - 2022 :overpaid\">\n    <h2 class=\"govuk-heading-s card-heading\">\n      \n\n\n\n    <a href=\"/tax-you-paid/2022-2023/paid-too-much\" class=\"govuk-link\"   >6 April 2022 to 5 April 2023</a>\n\n\n\n    </h2>\n\n    \n        \n<p  class=\"card-body owe_message\">HMRC owe you £84.23 .</p>\n\n        \n<p  class=\"card-body\">Get your refund paid online.</p>\n\n      \n  </div>"}"""
      )
      val actual = json.as[SummaryCardPartial]
      actual.partialName mustBe "card1"
    }
  }
}
