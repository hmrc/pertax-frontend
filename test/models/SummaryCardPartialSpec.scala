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
        "partialName"                 -> "card1",
        "partialContent"              -> "<h2>test-html</h2>",
        "partialReconciliationStatus" -> Json.obj(
          "code" -> 5,
          "name" -> "Underpaid"
        ),
        "startTaxYear"                -> 2026
      )
      val actual = json.as[SummaryCardPartial]
      actual.partialName mustBe "card1"
      actual.partialContent.toString().contains("test-html")
    }
  }
}
