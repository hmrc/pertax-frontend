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

package models.addresslookup

import play.api.libs.json.Json

/**
  * Address typically represents a postal address.
  * For UK addresses, 'town' will always be present.
  * For non-UK addresses, 'town' may be absent and there may be an extra line instead.
  */
case class Address(
  lines: Seq[String],
  town: Option[String],
  otherLine: Option[String],
  postcode: String,
  country: Country,
  subdivision: Option[Subdivision]) {

  def isValid: Boolean = lines.nonEmpty

  def nonEmptyFields: List[String] = lines.toList ::: town.toList ::: List(postcode)

  /** Gets a conjoined representation, excluding the country. */
  def printable(separator: String): String = nonEmptyFields.mkString(separator)

  /** Gets a single-line representation, excluding the country. */
  def printable: String = printable(", ")

  def line1: String = if (lines.nonEmpty) lines.head else ""

  def line2: String = if (lines.size > 1) lines(1) else ""

  def line3: String = if (lines.size > 2) lines(2) else ""

  def line4: String = if (lines.size > 3) lines(3) else ""

  def line5: String = if (lines.size > 4) lines(4) else ""
}

object Address {
  implicit val formats = Json.format[Address]
}
