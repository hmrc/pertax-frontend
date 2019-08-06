/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.data.validation.ValidationError
import play.api.libs.json._

object ResidencyChoiceDto {

  implicit val formats = {
    implicit val addrTypeReads: Reads[AddrType] = new Reads[AddrType] {
      override def reads(json: JsValue): JsResult[AddrType] = json match {
        case JsString("sole")    => JsSuccess(SoleAddrType)
        case JsString("primary") => JsSuccess(PrimaryAddrType)
        case JsString("postal")  => JsSuccess(PostalAddrType)
        case _                   => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.jsString(addrType)"))))
      }
    }

    implicit val addrTypeWrites: Writes[AddrType] = new Writes[AddrType] {
      override def writes(o: AddrType): JsValue = o match {
        case SoleAddrType    => JsString("sole")
        case PrimaryAddrType => JsString("primary")
        case PostalAddrType  => JsString("postal")
      }
    }
    Json.format[ResidencyChoiceDto]
  }

  val form = Form(
    mapping(
      "residencyChoice" -> optional(text)
        .verifying("error.you_must_select_an_answer", e => e.flatMap(a => AddrType(a)).isDefined)
        .transform[AddrType](x => AddrType(x.fold("")(_.toString)).getOrElse(SoleAddrType), ad => Some(ad.toString)) //getOrElse here will never fall back to default because of isDefined above
    )(ResidencyChoiceDto.apply)(ResidencyChoiceDto.unapply)
  )
}

case class ResidencyChoiceDto(residencyChoice: AddrType)
