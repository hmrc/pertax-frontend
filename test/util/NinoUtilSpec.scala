/*
 * Copyright 2026 HM Revenue & Customs
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
import uk.gov.hmrc.domain.Nino

class NinoUtilSpec extends BaseSpec {

  "NinoUtil.isNinoEligibleForPtapHomepage" should {

    "return true when the last numeric digit is in the configured list" in {
      val config   = Configuration("feature.ptap-homepage.nino-rollout.last-numeric-digits" -> Seq(4, 9))
      val ninoUtil = new NinoUtil()(config)

      ninoUtil.isNinoEligibleForPtapHomepage(Nino("AB123459C")) shouldBe true
    }

    "return false when the last numeric digit is not in the configured list" in {
      val config   = Configuration("feature.ptap-homepage.nino-rollout.last-numeric-digits" -> Seq(4, 9))
      val ninoUtil = new NinoUtil()(config)

      ninoUtil.isNinoEligibleForPtapHomepage(Nino("AB123458C")) shouldBe false
    }

    "return false when the configured list is empty" in {
      val config   = Configuration("feature.ptap-homepage.nino-rollout.last-numeric-digits" -> Seq.empty[Int])
      val ninoUtil = new NinoUtil()(config)

      ninoUtil.isNinoEligibleForPtapHomepage(Nino("AB123459C")) shouldBe false
    }

    "use default empty config when the key is not present" in {
      val config   = Configuration()
      val ninoUtil = new NinoUtil()(config)

      ninoUtil.isNinoEligibleForPtapHomepage(Nino("AB123459C")) shouldBe false
      ninoUtil.isNinoEligibleForPtapHomepage(Nino("AB123450C")) shouldBe false
    }

    "handle custom configured digit values" in {
      val config   = Configuration("feature.ptap-homepage.nino-rollout.last-numeric-digits" -> Seq(1, 2, 5))
      val ninoUtil = new NinoUtil()(config)

      ninoUtil.isNinoEligibleForPtapHomepage(Nino("AB123455C")) shouldBe true
      ninoUtil.isNinoEligibleForPtapHomepage(Nino("AB123459C")) shouldBe false
    }
  }
}
