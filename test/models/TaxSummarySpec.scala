/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.Json
import util.{BaseSpec, Fixtures}


class TaxSummarySpec extends BaseSpec {

  "Calling TaxSummary.fromJsonTaxSummaryDetails" should {
    
    "produce a valid TaxSummary object when supplied good json" in {
      
      val ts = TaxSummary.fromJsonTaxSummaryDetails(Json.parse(Fixtures.exampleTaxSummaryDetailsJson))
      ts shouldBe Fixtures.buildTaxSummary
    }
    
  }

  "Calling TaxSummary.isMarriageAllowanceRecipient" should {

    "return true if the tax code ends in M when supplied with good json" in {

      val ts = TaxSummary.fromJsonTaxSummaryDetails(Json.parse(Fixtures.exampleTaxSummaryDetailsJsonTaxCodeEndsM))
      val isMARecipient = ts.isMarriageAllowanceRecipient
      isMARecipient shouldBe true
    }

    "return true if the tax code ends in N when supplied with good json" in {

      val ts = TaxSummary.fromJsonTaxSummaryDetails(Json.parse(Fixtures.exampleTaxSummaryDetailsJsonTaxCodeEndsN))
      val isMARecipient = ts.isMarriageAllowanceRecipient
      isMARecipient shouldBe true
    }

    "return false if the tax code doesn't end in M or N when supplied with good json" in {

      val ts = TaxSummary.fromJsonTaxSummaryDetails(Json.parse(Fixtures.exampleTaxSummaryDetailsJson))
      val isMARecipient = ts.isMarriageAllowanceRecipient
      isMARecipient shouldBe false
    }
  }

  "Calling TaxSummary.isCompanyBenefitRecipient" should {

    "return true if Iabd type is 30 (Medical Insurance) and iabd type is 31 (Car Benefit) when supplied with good json" in {
      val ts = TaxSummary.fromJsonTaxSummaryDetails(Json.parse(Fixtures.exampleTaxSummaryDetailsJsonBothCompanyBenefits))
      val isCompanyBenefit = ts.isCompanyBenefitRecipient
      isCompanyBenefit shouldBe true
    }

    "return false if no Iabd type is supplied with good json" in {
      val ts = TaxSummary.fromJsonTaxSummaryDetails(Json.parse(Fixtures.exampleTaxSummaryDetailsJson))
      val isCompanyBenefit = ts.isCompanyBenefitRecipient
      isCompanyBenefit shouldBe false
    }

    "return true if there is only one Iabd type equal to 30 (Medical Insurance) when supplied with good json" in {
      val ts = TaxSummary.fromJsonTaxSummaryDetails(Json.parse(Fixtures.exampleTaxSummaryDetailsJsonCompanyBenefitsMedicalOnly))
      val isCompanyBenefit = ts.isCompanyBenefitRecipient
      isCompanyBenefit shouldBe true
    }

    "return true if there is only one Iabd type equal to 31 (Car Benefit) when supplied with good json" in {
      val ts = TaxSummary.fromJsonTaxSummaryDetails(Json.parse(Fixtures.exampleTaxSummaryDetailsJsonCompanyBenefitsCarOnly))
      val isCompanyBenefit = ts.isCompanyBenefitRecipient
      isCompanyBenefit shouldBe true
    }

  }
}
