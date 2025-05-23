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
import util.PertaxValidators._
case class AddressFinderDto(postcode: String, filter: Option[String]) extends Dto

object AddressFinderDto {

  def unapply(obj: AddressFinderDto): Some[(String, Option[String])] = Some((obj.postcode, obj.filter))

  implicit val formats: OFormat[AddressFinderDto] = Json.format[AddressFinderDto]

  val form: Form[AddressFinderDto] = Form(
    mapping(
      "postcode" -> text
        .verifying(
          "error.enter_a_valid_uk_postcode",
          e =>
            e match {
              case PostcodeRegex(_*) => true
              case _                 => false
            }
        ),
      "filter"   -> optional(nonEmptyText)
        .verifying("error.enter_valid_characters", e => validateAddressLineCharacters(e))
    )(AddressFinderDto.apply)(AddressFinderDto.unapply)
  )
}
