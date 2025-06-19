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

import controllers.bindable._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._

case class ResidencyChoiceDto(residencyChoice: AddrType) extends Dto

object ResidencyChoiceDto {
  def unapply(obj: ResidencyChoiceDto): Some[AddrType] = Some(obj.residencyChoice)

  implicit val formats: OFormat[ResidencyChoiceDto] = {
    implicit val addrTypeReads: Reads[AddrType] = {
      case JsString("residential") => JsSuccess(ResidentialAddrType)
      case JsString("postal")      => JsSuccess(PostalAddrType)
      case _                       => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.jsString(addrType)"))))
    }

    implicit val addrTypeWrites: Writes[AddrType] = {
      case ResidentialAddrType => JsString("residential")
      case PostalAddrType      => JsString("postal")
    }
    Json.format[ResidencyChoiceDto]
  }

  val form = Form(
    mapping(
      "residencyChoice" -> optional(text)
        .verifying("error.multiple_address_select", e => e.flatMap(a => AddrType(a)).isDefined)
        .transform[AddrType](
          x => AddrType(x.fold("")(_.toString)).getOrElse(ResidentialAddrType),
          ad => Some(ad.toString)
        ) //getOrElse here will never fall back to default because of isDefined above
    )(ResidencyChoiceDto.apply)(ResidencyChoiceDto.unapply)
  )
}
