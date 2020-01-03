/*
 * Copyright 2020 HM Revenue & Customs
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

package models.dto

import org.joda.time.LocalDate
import util.BaseSpec

class DateDtoSpec extends BaseSpec {

  "Posting the enterStartDateForm" should {

    "bind DateDto correctly when given valid data" in {
      val formData = Map(
        "startDate.day"   -> "1",
        "startDate.month" -> "1",
        "startDate.year"  -> new LocalDate().minusYears(1).getYear.toString
      )

      val previousYear = new LocalDate(new LocalDate().minusYears(1).getYear, 1, 1)

      DateDto
        .form(LocalDate.now())
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length shouldBe 0
          },
          success => {
            success shouldBe DateDto(previousYear)
          }
        )

    }

    "return error when future date is submitted" in {

      val formData = Map(
        "startDate.day"   -> "1",
        "startDate.month" -> "1",
        "startDate.year"  -> new LocalDate().plusYears(1).getYear.toString
      )

      DateDto
        .form(LocalDate.now())
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length shouldBe 1
            formWithErrors.errors.head.message shouldBe "error.date_in_future"
          },
          success => {
            fail("Form should give an error")
          }
        )
    }

    "return an error date is before 01/01/1000" in {

      val formData = Map(
        "startDate.day"   -> "1",
        "startDate.month" -> "1",
        "startDate.year"  -> "0999"
      )

      DateDto
        .form(LocalDate.now())
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length shouldBe 1
            formWithErrors.errors.head.message shouldBe "error.enter_valid_date"
          },
          success => {
            fail("Form should give an error")
          }
        )
    }
  }
}
