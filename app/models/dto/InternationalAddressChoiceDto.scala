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

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{Json, OFormat}

case class InternationalAddressChoiceDto(value: String) extends Dto

object InternationalAddressChoiceDto {

  implicit val formats: OFormat[InternationalAddressChoiceDto] = Json.format[InternationalAddressChoiceDto]

  val ukOptions: List[String] = List("england", "ni", "scotland", "wales")

  def form(errorMessageKey: Option[String] = None): Form[InternationalAddressChoiceDto] =
    Form(
      mapping(
        "internationalAddressChoice" -> optional(text)
          .verifying(errorMessageKey.getOrElse("error.international_address_select.required"), _.isDefined)
          .transform[String](_.get, Some(_))
      )(InternationalAddressChoiceDto.apply)(InternationalAddressChoiceDto.unapply)
    )
}
