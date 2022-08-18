/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.Logging
import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.LocalDate

case class Address(
  line1: Option[String],
  line2: Option[String],
  line3: Option[String],
  line4: Option[String],
  line5: Option[String],
  postcode: Option[String],
  country: Option[String],
  startDate: Option[LocalDate],
  endDate: Option[LocalDate],
  `type`: Option[String],
  isRls: Boolean
) {
  lazy val lines = List(line1, line2, line3, line4, line5).flatten
  lazy val fullAddress =
    List(line1, line2, line3, line4, line5, postcode.map(_.toUpperCase), internationalAddressCountry(country)).flatten

  val excludedCountries = List(
    Country("GREAT BRITAIN"),
    Country("SCOTLAND"),
    Country("ENGLAND"),
    Country("WALES"),
    Country("NORTHERN IRELAND")
  )

  def internationalAddressCountry(country: Option[String]): Option[String] =
    excludedCountries.contains(Country(country.getOrElse(""))) match {
      case false => country
      case _     => None
    }

  def isWelshLanguageUnit: Boolean = {
    val welshLanguageUnitPostcodes = Set("CF145SH", "CF145TS", "LL499BF", "BX55AB", "LL499AB")
    welshLanguageUnitPostcodes.contains(postcode.getOrElse("").toUpperCase.trim.replace(" ", ""))
  }
}

object Address extends Logging {

  implicit val writes: Writes[Address] = new Writes[Address] {
    override def writes(o: Address): JsValue =
      removeNulls(
        Json.obj(
          "line1"     -> o.line1,
          "line2"     -> o.line2,
          "line3"     -> o.line3,
          "line4"     -> o.line4,
          "line5"     -> o.line5,
          "postcode"  -> o.postcode,
          "country"   -> o.country,
          "startDate" -> o.startDate,
          "endDate"   -> o.endDate,
          "type"      -> o.`type`
        )
      )
  }

  implicit val reads: Reads[Address] = (
    (JsPath \ "line1").readNullable[String] and
      (JsPath \ "line2").readNullable[String] and
      (JsPath \ "line3").readNullable[String] and
      (JsPath \ "line4").readNullable[String] and
      (JsPath \ "line5").readNullable[String] and
      (JsPath \ "postcode").readNullable[String] and
      (JsPath \ "country").readNullable[String] and
      (JsPath \ "startDate").readNullable[LocalDate] and
      (JsPath \ "endDate").readNullable[LocalDate] and
      (JsPath \ "type").readNullable[String] and
      (JsPath \ "status").readNullable[Int].map(isRls)
  )(Address.apply _)

  private def removeNulls(jsObject: JsObject): JsValue =
    JsObject(jsObject.fields.collect {
      case (s, j: JsObject) =>
        (s, removeNulls(j))
      case other if other._2 != JsNull =>
        other
    })

  private def isRls(status: Option[Int]): Boolean =
    status match {
      case None =>
        false

      case Some(status) =>
        status match {
          case 0 => false
          case 1 => true
          case invalid =>
            val ex = new IllegalArgumentException(s"Status $invalid is not a valid value for the RLS indicator")
            logger.error(ex.getMessage, ex)
            false
        }
    }
}
