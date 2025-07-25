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

import testUtils.BaseSpec
import util.PertaxValidators._

class PertaxValidatorsSpec extends BaseSpec {

  "validateAddressLineCharacters" must {

    "return false when an illegal character is used" in {
      for (char <- """£!#$%*+:;<=>?@[\]^_"{|}~""")
        validateAddressLineCharacters(Some(char.toString)) mustBe false
    }

    "return false when illegal characters are used" in {
      validateAddressLineCharacters(Some("""45b Mühlendamm NE32 5RS""")) mustBe false
    }

    "return true when no illegal characters are used" in {
      validateAddressLineCharacters(Some("""48/- Williams Park, Tyne & Wear NE32-5RS""")) mustBe true
    }

  }
}
