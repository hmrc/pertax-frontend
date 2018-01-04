/*
 * Copyright 2018 HM Revenue & Customs
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

/*
 * Copyright 2015 HM Revenue & Customs
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

import play.api.libs.json.{JsValue, Json}

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

//-------------------------------------------------------------------------------------------------

/**
  * Address typically represents a postal address.
  * For UK addresses, 'town' will always be present.
  * For non-UK addresses, 'town' may be absent and there may be an extra line instead.
  */
case class Address(lines: Seq[String],
                   town: Option[String],
                   otherLine: Option[String],
                   postcode: String,
                   country: Country) {

  def isValid = lines.nonEmpty && lines.size <= (if (town.isEmpty) 4 else 3)

  def nonEmptyFields: List[String] = lines.toList ::: town.toList ::: List(postcode)

  /** Gets a conjoined representation, excluding the country. */
  def printable(separator: String): String = nonEmptyFields.mkString(separator)

  /** Gets a single-line representation, excluding the country. */
  def printable: String = printable(", ")

  // Gets line1 if non-empty. */
  def line1 = if (lines.nonEmpty) lines.head else ""

  // Gets line2 if non-empty. */
  def line2 = if (lines.size > 1) lines(1) else ""

  // Gets line3 if non-empty. */
  def line3 = if (lines.size > 2) lines(2) else ""

  // Gets line4 if non-empty. */
  def line4 = if (lines.size > 3) lines(3) else ""

  // Gets line5 if non-empty. */
  def line5 = if (lines.size > 4) lines(4) else ""
}

object Address {
  implicit val formats = Json.format[Address]
}

//-------------------------------------------------------------------------------------------------

/**
  * Represents one address record. Arrays of these are returned from the address-lookup microservice.
  */
case class AddressRecord(
                          id: String,
                          address: Address,
                          // ISO639-1 code, e.g. 'en' for English
                          // see https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
                          language: String) {

  def isValid = address.isValid && language.length == 2
}

object AddressRecord {
  implicit val formats = Json.format[AddressRecord]
}

//-------------------------------------------------------------------------------------------------

case class RecordSet(addresses: Seq[AddressRecord])

object RecordSet {
  def fromJsonAddressLookupService(addressListAsJson: JsValue): RecordSet = {
    val addresses = addressListAsJson.as[Seq[AddressRecord]]
    RecordSet(addresses)
  }
}
