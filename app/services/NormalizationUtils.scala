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

import models.dto.InternationalAddressChoiceDto
import javax.inject.{Inject, Singleton}

@Singleton
class NormalizationUtils @Inject() {
  private def normPostcode(pc: Option[String]): String = pc.getOrElse("").replace(" ", "").toUpperCase

  def samePostcode(a: Option[String], b: Option[String]): Boolean = normPostcode(a) == normPostcode(b)

  def normCountry(c: Option[String]): String =
    c.getOrElse("").trim.toUpperCase.replaceAll("\\s+", "")

  def normCountryFromChoice(choice: Option[InternationalAddressChoiceDto]): String =
    normCountry(choice.map(_.toString))

  private def isScotland(cNorm: String): Boolean = cNorm == "SCOTLAND"

  def isCrossBorderScotland(oldCN: String, newCN: String): Boolean = isScotland(oldCN) ^ isScotland(newCN)
}
