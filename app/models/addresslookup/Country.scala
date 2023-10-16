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

package models.addresslookup

import play.api.libs.json.{Json, OFormat}

case class Country(
  // ISO3166-1 or ISO3166-2 code, e.g. "GB" or "GB-ENG" (note that "GB" is the official
  // code for UK although "UK" is a reserved synonym and may be used instead)
  // See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
  // and https://en.wikipedia.org/wiki/ISO_3166-2:GB
  code: String,
  // The printable name for the country, e.g. "United Kingdom"
  name: String
) {}

object Country {
  implicit val formats: OFormat[Country] = Json.format[Country]
  // note that "GB" is the official ISO code for UK, although "UK" is a reserved synonym and is less confusing
  val UK: Country                        = Country("UK", "United Kingdom")
  private val GB: Country                = Country("GB", "United Kingdom") // special case provided for in ISO-3166
  private val GG                         = Country("GG", "Guernsey")
  private val IM                         = Country("IM", "Isle of Man")
  private val JE                         = Country("JE", "Jersey")

  val England: Country        = Country("GB-ENG", "England")
  val Scotland: Country       = Country("GB-SCT", "Scotland")
  private val Wales           = Country("GB-WLS", "Wales")
  private val Cymru           = Country("GB-CYM", "Cymru")
  private val NorthernIreland = Country("GB-NIR", "Northern Ireland")

  private val all = List(UK, GB, GG, IM, JE, England, Scotland, Wales, Cymru, NorthernIreland)

  def find(code: String): Option[Country] = all.find(_.code == code)
}
