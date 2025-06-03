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

package models

import play.api.libs.json.JsValue

object TaxComponents {
  def fromJsonTaxComponents(taxComponents: JsValue): List[String] =
    (taxComponents \\ "componentType").map(_.as[String]).toList

  def isMarriageAllowanceRecipient(taxComponents: List[String]): Boolean =
    taxComponents.contains("MarriageAllowanceReceived")

  def isMarriageAllowanceTransferor(taxComponents: List[String]): Boolean =
    taxComponents.contains("MarriageAllowanceTransferred")

  def notMarriageAllowanceCustomer(taxComponents: List[String]): Boolean =
    !(isMarriageAllowanceRecipient(taxComponents) || isMarriageAllowanceTransferor(taxComponents))

  def isCompanyBenefitRecipient(taxComponents: List[String]): Boolean =
    taxComponents.exists(componentType => componentType == "CarBenefit" || componentType == "MedicalInsurance")
}
