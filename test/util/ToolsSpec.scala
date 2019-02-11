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

package util

import com.typesafe.config.ConfigFactory
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.crypto.ApplicationCrypto

import scala.collection.JavaConverters._

class ToolsSpec extends BaseSpec with OneAppPerSuite {

  val config = ConfigFactory.parseMap(
    Map(
      "cookie.encryption.key"         -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "sso.encryption.key"            -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "json.encryption.key"           -> "gvBoGdgzqG1AarzF1LY0zQ=="
    ).asJava
  )

  val tools = new Tools(new ApplicationCrypto(config))

  "Calling urlEncode" should {

    "return encoded url for http://www.gov.uk?paramOne=1&paramTwo=2" in {
      tools.urlEncode("http://www.gov.uk?paramOne=1&paramTwo=2") shouldBe "http%3A%2F%2Fwww.gov.uk%3FparamOne%3D1%26paramTwo%3D2"
    }
  }

  "Calling encryptAndEncode" should {

    "return encrypted and encoded url for http://www.gov.uk?paramOne=1&paramTwo=2" in {
      tools.encryptAndEncode("http://www.gov.uk?paramOne=1&paramTwo=2") shouldBe "gxiBIOGIbn6eyoQ1PgijeECP4%2F8Ws7lUHnpPikndMN76jtk0UZawzrY2sYqwHMJU"
    }
  }

  "Calling isRelative" should {

    "return true for /" in {
      tools.isRelative("/") shouldBe true
    }

    "return true for /personal-account" in {
      tools.isRelative("/personal-account") shouldBe true
    }

    "return true for /personal-account/tax-credits-summary" in {
      tools.isRelative("/personal-account/tax-credits-summary") shouldBe true
    }

    "return true for /123" in {
      tools.isRelative("/123") shouldBe true
    }

    "return true for ''" in {
      tools.isRelative("") shouldBe true
    }

    "return false for //protocolUrl" in {
      tools.isRelative("//protocolUrl") shouldBe false
    }

    "return false for http://www.gov.uk" in {
      tools.isRelative("http://www.gov.uk") shouldBe false
    }

    "return false for https://www.gov.uk" in {
      tools.isRelative("https://www.gov.uk") shouldBe false
    }
  }
}
