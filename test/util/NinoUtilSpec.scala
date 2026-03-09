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

package util

import org.scalatest.matchers.should.Matchers.shouldBe
import play.api.Configuration
import testUtils.BaseSpec

class NinoUtilSpec extends BaseSpec {
  "NinoUtil.shouldShowNewLayoutForNino" should {

    "return true when the last numeric digit matches the config digit (default 9)" in {
      val config   = Configuration("onboarding.by.nino.lastNumericDigit" -> Seq(4, 9))
      val ninoUtil = new NinoUtil()(config)

      ninoUtil.shouldShowNewLayoutForNino("AB123459C") shouldBe true
    }

    "return false when the last numeric digit does not match the config digit (default 9)" in {
      val config   = Configuration("onboarding.by.nino.lastNumericDigit" -> Seq(4, 9))
      val ninoUtil = new NinoUtil()(config)

      ninoUtil.shouldShowNewLayoutForNino("AB123458C") shouldBe false
    }

    "use default config value (9) when not specified in configuration" in {
      val config   = Configuration()
      val ninoUtil = new NinoUtil()(config)

      ninoUtil.shouldShowNewLayoutForNino("AB999999C") shouldBe true
      ninoUtil.shouldShowNewLayoutForNino("AB999998C") shouldBe false
    }

    "handle custom config digit values" in {
      val config   = Configuration("onboarding.by.nino.lastNumericDigit" -> Seq(1, 2, 5))
      val ninoUtil = new NinoUtil()(config)

      ninoUtil.shouldShowNewLayoutForNino("AB123455C") shouldBe true
      ninoUtil.shouldShowNewLayoutForNino("AB123459C") shouldBe false
    }
  }
}
