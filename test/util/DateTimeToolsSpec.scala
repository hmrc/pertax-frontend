/*
 * Copyright 2021 HM Revenue & Customs
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

import org.joda.time.{LocalDate => JodaLocalDate}

import java.time.LocalDateTime

class DateTimeToolsSpec extends BaseSpec {

  "Calling asHumanDateFromUnixDate" must {

    "return correctly formatted readable date when provided with a valid date" in {
      DateTimeTools.asHumanDateFromUnixDate("2018-01-01") mustBe "01 January 2018"
    }

    "return passed date when provided with an invalid date" in {
      DateTimeTools.asHumanDateFromUnixDate("INVALID DATE FORMAT") mustBe "INVALID DATE FORMAT"
    }
  }

  "Calling toPaymentDate" must {

    "return a correctly formatted date" in {

      DateTimeTools.toPaymentDate(LocalDateTime.parse("2019-11-25T13:13:51.755")) mustBe
        new JodaLocalDate(2019, 11, 25)
    }
  }
}
