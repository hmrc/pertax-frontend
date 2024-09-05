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

package models.dto

import java.time.LocalDate
import testUtils.BaseSpec

class DateDtoSpec extends BaseSpec {

  "Posting the enterStartDateForm" must {

    "bind DateDto correctly when given valid data" in {
      val formData = Map(
        "startDate.day"   -> "1",
        "startDate.month" -> "1",
        "startDate.year"  -> LocalDate.now.minusYears(1).getYear.toString
      )

      val previousYear = LocalDate.of(LocalDate.now.minusYears(1).getYear, 1, 1)

      DateDto
        .form(LocalDate.now())
        .bind(formData)
        .fold(
          formWithErrors => formWithErrors.errors.length mustBe 0,
          success => success mustBe DateDto(previousYear)
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
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.enter_valid_date"
          },
          _ => fail("Form should give an error")
        )
    }
  }
}
