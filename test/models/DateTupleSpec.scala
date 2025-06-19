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

import play.api.data.FormError
import models.DateFields._
import testUtils.BaseSpec
import java.time.LocalDate

class DateTupleSpec extends BaseSpec {

  "dateTuple" must {

    import models.DateTuple._

    trait Setup {

      def input: Map[String, String]

      lazy val result: Either[Seq[FormError], Option[LocalDate]] = dateTuple.bind(input)
    }

    "return error.date.required.day when day field is missing" in new Setup {

      lazy val input: Map[String, String] = Map(month -> "2", year -> "2015")
      result mustBe a[Left[_, _]]
      result.swap.getOrElse(Seq(FormError("Invalid", "invalid"))) mustBe Seq(FormError("", "error.date.required.day"))
    }

    "return error.date.required.month when month field is missing" in new Setup {

      lazy val input: Map[String, String] = Map(day -> "1", year -> "2015")
      result mustBe a[Left[_, _]]
      result.swap.getOrElse(Seq(FormError("Invalid", "invalid"))) mustBe Seq(FormError("", "error.date.required.month"))
    }

    "return error.date.required.year when year field is missing" in new Setup {

      lazy val input: Map[String, String] = Map(day -> "1", month -> "2")
      result mustBe a[Left[_, _]]
      result.swap.getOrElse(Seq(FormError("Invalid", "invalid"))) mustBe Seq(FormError("", "error.date.required.year"))
    }

    "return error.invalid.date.format when day field is non-digit" in new Setup {

      lazy val input: Map[String, String] = Map(day -> "@", month -> "2", year -> "2015")
      result mustBe a[Left[_, _]]
      result.swap.getOrElse(Seq(FormError("Invalid", "invalid"))) mustBe Seq(FormError("", "error.invalid.date.format"))
    }

    "return error.invalid.date.format when month field is non-digit" in new Setup {

      lazy val input: Map[String, String] = Map(day -> "1", month -> "j", year -> "2015")
      result mustBe a[Left[_, _]]
      result.swap.getOrElse(Seq(FormError("Invalid", "invalid"))) mustBe Seq(FormError("", "error.invalid.date.format"))
    }

    "return error.invalid.date.format when year field is non-digit" in new Setup {

      lazy val input: Map[String, String] = Map(day -> "1", month -> "2", year -> "%")
      result mustBe a[Left[_, _]]
      result.swap.getOrElse(Seq(FormError("Invalid", "invalid"))) mustBe Seq(FormError("", "error.invalid.date.format"))
    }

    "return error.invalid.date.format when date is not real" in new Setup {

      lazy val input: Map[String, String] = Map(day -> "30", month -> "2", year -> "2015")
      result mustBe a[Left[_, _]]
      result.swap.getOrElse(Seq(FormError("Invalid", "invalid"))) mustBe Seq(FormError("", "error.invalid.date.format"))
    }

    "return a date on valid input" in new Setup {

      lazy val input: Map[String, String] = Map(day -> "28", month -> "2", year -> "2015")
      result mustBe a[Right[_, _]]
      result.getOrElse(LocalDate.of(1, 1, 1)) mustBe Some(LocalDate.parse("2015-02-28"))
    }

  }

  "mandatoryDateTuple" must {

    import models.DateTuple._

    val errorKey = "error.date.required"

    def assertError(dateFields: Map[String, String], validationError: String): Unit = {
      val result = mandatoryDateTuple(errorKey).bind(dateFields)

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(Seq(FormError("Invalid", "invalid"))) mustBe Seq(FormError("", validationError))
    }

    "create a mapping for a valid date" in {
      val dateFields = Map(day -> "1", month -> "2", year -> "2014")
      val result     = mandatoryDateTuple(errorKey).bind(dateFields)

      result mustBe a[Right[_, _]]
      result.getOrElse(LocalDate.of(1, 1, 1)) mustBe LocalDate.of(2014, 2, 1)
    }

    "create a mapping for an invalid date (with space after month, day and year)" in {
      val dateFields = Map(day -> "1 ", month -> "2 ", year -> "2014 ")
      val result     = mandatoryDateTuple(errorKey).bind(dateFields)

      result mustBe a[Right[_, _]]
      result.getOrElse(LocalDate.of(1, 1, 1)) mustBe LocalDate.of(2014, 2, 1)
    }

    "return error when all the fields are empty" in
      assertError(Map(day -> "", month -> "", year -> ""), errorKey)

    "return a validation error for invalid date with characters" in
      assertError(Map(day -> "1", month -> "2", year -> "bla"), "error.invalid.date.format")

    "return a validation error for invalid date with invalid month" in
      assertError(Map(day -> "1", month -> "23", year -> "2014"), "error.invalid.date.format")

    "return a validation error for invalid date with only 2 digit year" in
      assertError(Map(day -> "1", month -> "2", year -> "14"), "error.invalid.date.format")

    "return a validation error for invalid date with more than 4 digit year" in
      assertError(Map(day -> "1", month -> "01", year -> "14444"), "error.invalid.date.format")

    "return a validation error for invalid date with more than 2 digit day" in
      assertError(Map(day -> "122", month -> "01", year -> "2014"), "error.invalid.date.format")

    "return a validation error for invalid date with more than 2 digit month" in
      assertError(Map(day -> "1", month -> "133", year -> "2014"), "error.invalid.date.format")

    "return error.date.required.day when day field is missing" in
      assertError(Map(month -> "2", year -> "2015"), "error.date.required.day")

    "return error.date.required.month when month field is missing" in
      assertError(Map(day -> "1", year -> "2015"), "error.date.required.month")

    "return error.date.required.year when year field is missing" in
      assertError(Map(day -> "1", month -> "2"), "error.date.required.year")
  }
}
