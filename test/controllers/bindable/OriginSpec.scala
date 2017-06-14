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

package controllers.bindable

import util.BaseSpec

class OriginSpec extends BaseSpec {

  "The validation of an origin" should {

    "pass with valid PTA origin" in {
      Origin("PERTAX").isValid shouldBe true
     }
    "pass with valid TCS origin" in {
      Origin("TCS").isValid shouldBe true
    }
    "pass with valid NISP origin" in {
      Origin("NISP").isValid shouldBe true
    }
    "pass with valid PAYE origin" in {
      Origin("PAYE").isValid shouldBe true
    }
    "pass with valid 'REPAYMENTS' origin" in {
      Origin("REPAYMENTS").isValid shouldBe true
    }
    "pass with valid P800 origin"  in {
      Origin("P800").isValid shouldBe true
    }
    "fail with an invalid origin"  in {
      Origin("INVALID").isValid shouldBe false
    }
  }
}
