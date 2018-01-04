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

package models

import play.api.libs.json.{Json, JsValue}

case class TaxSummary(taxCodes: Seq[String], companyBenefits: Seq[Int] ) {

  def isMarriageAllowanceTransferee = taxCodes.exists(taxCode => taxCode.toUpperCase.endsWith("M"))

  def isMarriageAllowanceTransferor = taxCodes.exists(taxCode => taxCode.toUpperCase.endsWith("N"))

  def notMarriageAllowanceCustomer = ! (isMarriageAllowanceTransferee || isMarriageAllowanceTransferor)

  def isCompanyBenefitRecipient: Boolean = {
    companyBenefits.exists(iabd => iabd == 31 || iabd == 30)
  }
}

object TaxSummary {
  def fromJsonTaxSummaryDetails(taxSummaryDetails: JsValue): TaxSummary = {

    val taxCodeIncomes = taxSummaryDetails \ "increasesTax" \ "incomes" \ "taxCodeIncomes" \ "employments" \ "taxCodeIncomes"
    val taxCodes = (taxCodeIncomes \\ "taxCode").map(_.as[String])

    val benefitsFromEmployment = taxSummaryDetails \ "increasesTax" \ "benefitsFromEmployment" \ "iabdSummaries"
    val iabdTypes = (benefitsFromEmployment \\ "iabdType")map(_.as[Int])

    TaxSummary(taxCodes, iabdTypes)
  }
}
