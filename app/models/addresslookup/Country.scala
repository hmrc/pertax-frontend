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

package models.addresslookup

import play.api.libs.json.Json

/** Represents a country as per ISO3166. */
case class Country(
  // ISO3166-1 or ISO3166-2 code, e.g. "GB" or "GB-ENG" (note that "GB" is the official
  // code for UK although "UK" is a reserved synonym and may be used instead)
  // See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
  // and https://en.wikipedia.org/wiki/ISO_3166-2:GB
  code: String,
  // The printable name for the country, e.g. "United Kingdom"
  name: String)

object Country {
  implicit val formats = Json.format[Country]
}
