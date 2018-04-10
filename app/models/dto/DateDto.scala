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

package models.dto

import org.joda.time.{LocalDate, DateTime}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import uk.gov.hmrc.play.mappers.DateTuple._

object DateDto {

  implicit val formats = Json.format[DateDto]

  def build(day: Int, month: Int, year: Int) = DateDto(new LocalDate(year, month, day))

  def form(today: LocalDate) = Form(
    mapping(
      "startDate" -> mandatoryDateTuple("error.enter_a_date")
        .verifying("error.date_in_future", !_.isAfter(today))
        .verifying("error.enter_valid_date", !_.isBefore(new LocalDate("1000-01-01")))
    )(DateDto.apply)(DateDto.unapply)
  )
}

case class DateDto(
  startDate: LocalDate
)
