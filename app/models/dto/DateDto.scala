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

import models.DateTuple._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate
import scala.annotation.unused

case class DateDto(
  startDate: LocalDate
) extends Dto

object DateDto {

  implicit val formats: OFormat[DateDto] = Json.format[DateDto]

  def unapply(obj: DateDto): Some[LocalDate] = Some(
    obj.startDate
  )

  def build(day: Int, month: Int, year: Int): DateDto = DateDto(LocalDate.of(year, month, day))

  def form(@unused today: LocalDate): Form[DateDto] = {
    val yearValidation  = 1000
    val monthValidation = 1
    val dayValidation   = 1
    Form(
      mapping(
        "startDate" -> mandatoryDateTuple("error.enter_a_date")
          .verifying(
            "error.enter_valid_date",
            !_.isBefore(LocalDate.of(yearValidation, monthValidation, dayValidation))
          )
      )(DateDto.apply)(DateDto.unapply)
    )
  }
}
