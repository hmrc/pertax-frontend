/*
 * Copyright 2021 HM Revenue & Customs
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

import org.joda.time.{DateTime, Instant, LocalDate}
import play.api.libs.json._
import _root_.util.DateTimeTools
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

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
  `type`: Option[String]
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

object Address {
  implicit val localdateFormatDefault = new Format[LocalDate] {
    override def reads(json: JsValue): JsResult[LocalDate] = JodaReads.DefaultJodaLocalDateReads.reads(json)
    override def writes(o: LocalDate): JsValue = JodaWrites.DefaultJodaLocalDateWrites.writes(o)
  }
  implicit val formats = Json.format[Address]
}
