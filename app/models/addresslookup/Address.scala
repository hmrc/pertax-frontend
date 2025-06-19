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

import com.fasterxml.jackson.annotation.JsonIgnore
import play.api.libs.json.{Json, OFormat}

/** Address typically represents a postal address. For UK addresses, 'town' will always be present. For non-UK
  * addresses, 'town' may be absent and there may be an extra line instead.
  */
case class Address(
  lines: List[String],
  town: Option[String],
  county: Option[String],
  postcode: String,
  subdivision: Option[Country],
  country: Country
) {

  private val lineCountWithoutTown = 4
  private val lineCountWithTown    = 3
  @JsonIgnore // needed because the name starts 'is...'
  def isValid: Boolean             = lines.nonEmpty && lines.size <= (if (town.isEmpty) lineCountWithoutTown else lineCountWithTown)

  def line1: String = if (lines.nonEmpty) lines.head else ""

  def line2: String = if (lines.size > 1) lines(1) else ""

  def line3: String = if (lines.size > 2) lines(2) else ""

  def line4: String = if (lines.size > 3) lines(3) else ""
}

object Address {
  implicit val formats: OFormat[Address] = Json.format[Address]
}
