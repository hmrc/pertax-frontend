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

case class TaxComponents(taxComponents: List[String]) {

  def isMarriageAllowanceRecipient: Boolean = true// taxComponents.contains("MarriageAllowanceReceived")

  def isMarriageAllowanceTransferor: Boolean = taxComponents.contains("MarriageAllowanceTransferred")

  def notMarriageAllowanceCustomer: Boolean = !(isMarriageAllowanceRecipient || isMarriageAllowanceTransferor)

  def isCompanyBenefitRecipient: Boolean =
    taxComponents.exists(componentType => componentType == "CarBenefit" || componentType == "MedicalInsurance")
}

object TaxComponents {
  def fromJsonTaxComponents(taxComponents: JsValue): TaxComponents = {

    val componentTypes = (taxComponents \\ "componentType").map(_.as[String]).toList

    TaxComponents(componentTypes)
  }
}
