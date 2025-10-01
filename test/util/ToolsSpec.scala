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
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.ApplicationCrypto

class ToolsSpec extends BaseSpec {

  val tools = new Tools(inject[ApplicationCrypto])

  "Calling urlEncode" must {

    "return encoded url for http://www.gov.uk?paramOne=1&paramTwo=2" in {
      tools.urlEncode(
        "http://www.gov.uk?paramOne=1&paramTwo=2"
      ) mustBe "http%3A%2F%2Fwww.gov.uk%3FparamOne%3D1%26paramTwo%3D2"
    }
  }

  "Calling encryptAndEncode" must {

    "return encrypted and encoded url for http://www.gov.uk?paramOne=1&paramTwo=2" in {
      tools.encryptAndEncode(
        "http://www.gov.uk?paramOne=1&paramTwo=2"
      ) mustBe "gxiBIOGIbn6eyoQ1PgijeECP4%2F8Ws7lUHnpPikndMN76jtk0UZawzrY2sYqwHMJU"
    }
  }

  "Calling isRelative" must {

    "return true for /" in {
      tools.isRelative("/") mustBe true
    }

    "return true for /personal-account" in {
      tools.isRelative("/personal-account") mustBe true
    }

    "return true for /personal-account/tax-credits-summary" in {
      tools.isRelative("/personal-account/tax-credits-summary") mustBe true
    }

    "return true for /123" in {
      tools.isRelative("/123") mustBe true
    }

    "return true for ''" in {
      tools.isRelative("") mustBe true
    }

    "return false for //protocolUrl" in {
      tools.isRelative("//protocolUrl") mustBe false
    }

    "return false for http://www.gov.uk" in {
      tools.isRelative("http://www.gov.uk") mustBe false
    }

    "return false for https://www.gov.uk" in {
      tools.isRelative("https://www.gov.uk") mustBe false
    }
  }
}
