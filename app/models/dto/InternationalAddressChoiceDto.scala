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
import play.api.data.Forms.of
import play.api.libs.json.{JsString, Writes}
import util.{Enumerable, Formatters, WithName}

sealed trait InternationalAddressChoiceDto

object InternationalAddressChoiceDto extends Enumerable.Implicits with Formatters {
  case object OutsideUK extends WithName("outsideUK") with InternationalAddressChoiceDto
  case object England extends WithName("england") with InternationalAddressChoiceDto
  case object Scotland extends WithName("scotland") with InternationalAddressChoiceDto
  case object Wales extends WithName("wales") with InternationalAddressChoiceDto
  case object NorthernIreland extends WithName("ni") with InternationalAddressChoiceDto

  def isUk(country: Option[InternationalAddressChoiceDto]): Boolean =
    country match {
      case Some(OutsideUK) => false
      case _               => true
    }

  val values: Set[InternationalAddressChoiceDto] = Set(England, NorthernIreland, Scotland, Wales, OutsideUK)

  implicit val enumerable: Enumerable[InternationalAddressChoiceDto] =
    Enumerable(values.toSeq.map(v => v.toString -> v): _*)

  implicit lazy val writes: Writes[InternationalAddressChoiceDto]    = Writes {
    case OutsideUK       => JsString(OutsideUK.toString)
    case England         => JsString(England.toString)
    case Scotland        => JsString(Scotland.toString)
    case Wales           => JsString(Wales.toString)
    case NorthernIreland => JsString(NorthernIreland.toString)
  }

  def form(
    errorMessageKey: String = "error.international_address_select.required"
  ): Form[InternationalAddressChoiceDto] =
    Form(
      "internationalAddressChoice" -> of(
        enumerableFormatter[InternationalAddressChoiceDto](errorMessageKey, errorMessageKey)
      )
    )
}
