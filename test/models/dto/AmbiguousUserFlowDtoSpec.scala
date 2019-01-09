/*
 * Copyright 2019 HM Revenue & Customs
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

package models.dto

import util.BaseSpec

class AmbiguousUserFlowDtoSpec extends BaseSpec {

  "AmbiguousUserFlowDto" should {
    "fail on invalid data" in {
      AmbiguousUserFlowDto.form.bind(Map.empty[String, String]).fold(
        formErrors => {
          formErrors.errors.length shouldBe 1
          formErrors.errors.head.message shouldBe "error.enrolled.to.send.tax.required"

        },
        _ => fail("There is a problem")

      )

    }
  }

}
