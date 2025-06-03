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

import models.TaxComponents.{isCompanyBenefitRecipient, isMarriageAllowanceRecipient, isMarriageAllowanceTransferor, notMarriageAllowanceCustomer}
import play.api.libs.json.Json
import testUtils.BaseSpec

class TaxComponentsSpec extends BaseSpec {

  "Calling TaxComponents.fromJsonTaxComponents" must {

    "produce a valid TaxComponents object when supplied good json" in {

      val taxComponentsJson = Json.parse("""{
                                           |   "data" : [ {
                                           |      "componentType" : "EmployerProvidedServices",
                                           |      "employmentId" : 12,
                                           |      "amount" : 12321,
                                           |      "description" : "Some Description",
                                           |      "iabdCategory" : "Benefit"
                                           |   }, {
                                           |      "componentType" : "PersonalPensionPayments",
                                           |      "employmentId" : 31,
                                           |      "amount" : 12345,
                                           |      "description" : "Some Description Some",
                                           |      "iabdCategory" : "Allowance"
                                           |   } ],
                                           |   "links" : [ ]
                                           |}""".stripMargin)

      val tc = TaxComponents.fromJsonTaxComponents(taxComponentsJson)
      tc mustBe List("EmployerProvidedServices", "PersonalPensionPayments")
    }
  }

  "Checking marriage allowance status" must {

    "indicate a recipient but not a transferor if the tax code ends in M" in {
      val tc = List("MarriageAllowanceReceived")
      isMarriageAllowanceRecipient(tc) mustBe true
      isMarriageAllowanceTransferor(tc) mustBe false
      notMarriageAllowanceCustomer(tc) mustBe false
    }

    "indicate a transferor but not a transferee if the tax code ends in N" in {
      val tc = List("MarriageAllowanceTransferred")
      isMarriageAllowanceRecipient(tc) mustBe false
      isMarriageAllowanceTransferor(tc) mustBe true
      notMarriageAllowanceCustomer(tc) mustBe false
    }

    "indicate neither a transferor or a transferee if the tax code does not end in N or M" in {
      val tc = List("MedicalInsurance")
      isMarriageAllowanceRecipient(tc) mustBe false
      isMarriageAllowanceTransferor(tc) mustBe false
      notMarriageAllowanceCustomer(tc) mustBe true
    }
  }

  "Calling TaxComponents.isCompanyBenefitRecipient" must {

    "return true if both Medical Insurance and Car Benefit exists when supplied with good json" in {

      val taxComponentsJson = Json.parse("""{
                                           |  "data" : [ {
                                           |    "componentType" : "CarBenefit",
                                           |    "employmentId" : 12,
                                           |    "amount" : 100,
                                           |    "description" : "Some Description",
                                           |    "iabdCategory" : "Benefit"
                                           |  }, {
                                           |    "componentType" : "MedicalInsurance",
                                           |    "employmentId" : 31,
                                           |    "amount" : 200,
                                           |    "description" : "Some Description Some",
                                           |    "iabdCategory" : "Benefit"
                                           |  } ],
                                           |  "links" : [ ]
                                           |}""".stripMargin)

      val tc               = TaxComponents.fromJsonTaxComponents(taxComponentsJson)
      val isCompanyBenefit = isCompanyBenefitRecipient(tc)
      isCompanyBenefit mustBe true
    }

    "return false if no Iabd type is supplied with good json" in {

      val taxComponentsJson = Json.parse("""{
                                           |   "data" : [ {
                                           |      "componentType" : "EmployerProvidedServices",
                                           |      "employmentId" : 12,
                                           |      "amount" : 12321,
                                           |      "description" : "Some Description",
                                           |      "iabdCategory" : "Benefit"
                                           |   }, {
                                           |      "componentType" : "PersonalPensionPayments",
                                           |      "employmentId" : 31,
                                           |      "amount" : 12345,
                                           |      "description" : "Some Description Some",
                                           |      "iabdCategory" : "Allowance"
                                           |   } ],
                                           |   "links" : [ ]
                                           |}""".stripMargin)

      val tc               = TaxComponents.fromJsonTaxComponents(taxComponentsJson)
      val isCompanyBenefit = isCompanyBenefitRecipient(tc)
      isCompanyBenefit mustBe false
    }

    "return true if there is only one Iabd type equal to 30 (Medical Insurance) when supplied with good json" in {

      val taxComponentsJson = Json.parse("""{
                                           |  "data" : [ {
                                           |    "componentType" : "MedicalInsurance",
                                           |    "employmentId" : 12,
                                           |    "amount" : 150,
                                           |    "description" : "Some Description",
                                           |    "iabdCategory" : "Benefit"
                                           |  }, {
                                           |    "componentType" : "PersonalPensionPayments",
                                           |    "employmentId" : 31,
                                           |    "amount" : 12345,
                                           |    "description" : "Some Description Some",
                                           |    "iabdCategory" : "Allowance"
                                           |  } ],
                                           |  "links" : [ ]
                                           |}""".stripMargin)

      val tc               = TaxComponents.fromJsonTaxComponents(taxComponentsJson)
      val isCompanyBenefit = isCompanyBenefitRecipient(tc)
      isCompanyBenefit mustBe true
    }

    "return true if there is only one Iabd type equal to 31 (Car Benefit) when supplied with good json" in {

      val taxComponentsJson = Json.parse("""{
                                           |  "data" : [ {
                                           |    "componentType" : "CarBenefit",
                                           |    "employmentId" : 12,
                                           |    "amount" : 150,
                                           |    "description" : "Some Description",
                                           |    "iabdCategory" : "Benefit"
                                           |  }, {
                                           |    "componentType" : "PersonalPensionPayments",
                                           |    "employmentId" : 31,
                                           |    "amount" : 12345,
                                           |    "description" : "Some Description Some",
                                           |    "iabdCategory" : "Allowance"
                                           |  } ],
                                           |  "links" : [ ]
                                           |}""".stripMargin)

      val tc               = TaxComponents.fromJsonTaxComponents(taxComponentsJson)
      val isCompanyBenefit = isCompanyBenefitRecipient(tc)
      isCompanyBenefit mustBe true
    }

  }
}
