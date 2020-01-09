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

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json

final case class SAWrongCredentialsDto(value: Boolean)

object SAWrongCredentialsDto {

  implicit val formats = Json.format[SAWrongCredentialsDto]

  val form = Form(
    mapping(
      "wrongCredentialsFormChoice" -> optional(boolean)
        .verifying("error.you_must_select_an_answer", _.isDefined)
        .transform[Boolean](_.getOrElse(false), Some(_))
    )(SAWrongCredentialsDto.apply)(SAWrongCredentialsDto.unapply)
  )
}
