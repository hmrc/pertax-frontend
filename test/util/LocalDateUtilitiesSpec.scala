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

import java.time.LocalDate

class LocalDateUtilitiesSpec extends BaseSpec {

  private val localDateUtilities = new LocalDateUtilities()

  "Calling isBetween" must {

    "return true if the date is more than start date and less then end date" in {
      val startDate = LocalDate.MIN
      val endDate = LocalDate.MAX
      val dateToCheck = LocalDate.now()
      localDateUtilities.isBetween(dateToCheck, startDate, endDate) mustBe true
    }

    "return true if the date is equal to start date and less then end date" in {
      val startDate = LocalDate.MIN
      val endDate = LocalDate.MAX
      val dateToCheck = startDate
      localDateUtilities.isBetween(dateToCheck, startDate, endDate) mustBe true
    }

    "return true if the date is equal to end date and more then start date" in {
      val startDate = LocalDate.MIN
      val endDate = LocalDate.MAX
      val dateToCheck = endDate
      localDateUtilities.isBetween(dateToCheck, startDate, endDate) mustBe true
    }

    "return false if the date is before the start date" in {
      val startDate = LocalDate.now()
      val endDate = LocalDate.MAX
      val dateToCheck = LocalDate.MIN
      localDateUtilities.isBetween(dateToCheck, startDate, endDate) mustBe false
    }

    "return false if the date is after the end date" in {
      val startDate = LocalDate.MIN
      val endDate = LocalDate.now()
      val dateToCheck = LocalDate.MAX
      localDateUtilities.isBetween(dateToCheck, startDate, endDate) mustBe false
    }
  }
}
