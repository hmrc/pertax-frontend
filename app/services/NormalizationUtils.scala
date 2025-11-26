/*
 * Copyright 2025 HM Revenue & Customs
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

package services

import javax.inject.{Inject, Singleton}

@Singleton
class NormalizationUtils @Inject() {

  private def normalizePostcode(postcode: Option[String]): String =
    postcode.getOrElse("").replace(" ", "").toUpperCase

  def postcodesMatch(a: Option[String], b: Option[String]): Boolean =
    normalizePostcode(a) == normalizePostcode(b)

  def normalizeCountryName(countryOpt: Option[String]): String =
    countryOpt.getOrElse("").trim.toUpperCase.replaceAll("\\s+", "")

  private def isScottishCountry(normalized: String): Boolean =
    normalized == "SCOTLAND"

  def movedAcrossScottishBorder(oldCountry: String, newCountry: String): Boolean =
    isScottishCountry(oldCountry) ^ isScottishCountry(newCountry)

  def isUkCountry(normalized: String): Boolean =
    normalized match {
      case "UNITEDKINGDOM" | "ENGLAND" | "SCOTLAND" | "WALES" | "CYMRU" | "NORTHERNIRELAND" => true
      case _                                                                                => false
    }

  def isNonUkCountry(normalized: String): Boolean =
    !isUkCountry(normalized)
}
