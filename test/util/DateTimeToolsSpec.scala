/*
 * Copyright 2018 HM Revenue & Customs
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

class DateTimeToolsSpec extends BaseSpec {

  "Calling asHumanDateFromUnixDate" should {

    "return correctly formatted readable date when provided with a valid date" in {
      DateTimeTools.asHumanDateFromUnixDate("2018-01-01") shouldBe "01 January 2018"
    }

    "return passed date when provided with an invalid date" in {
      DateTimeTools.asHumanDateFromUnixDate("INVALID DATE FORMAT") shouldBe "INVALID DATE FORMAT"
    }
  }
}
