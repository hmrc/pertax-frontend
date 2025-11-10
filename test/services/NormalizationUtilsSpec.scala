/*
 * Copyright 2025 HM Revenue & Customs
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

package services

import models.dto.InternationalAddressChoiceDto
import play.api.Application
import play.api.inject.bind
import testUtils.BaseSpec

class NormalizationUtilsSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[NormalizationUtils].toInstance(new NormalizationUtils)
    )
    .build()

  private lazy val utils: NormalizationUtils = app.injector.instanceOf[NormalizationUtils]

  override def beforeEach(): Unit =
    super.beforeEach()

  "samePostcode" must {
    "return true when postcodes are equal ignoring spaces and case" in {
      utils.samePostcode(Some("EH1 1AA"), Some("eh11aa")) mustBe true
      utils.samePostcode(Some(" SW1A  2AA "), Some("sw1a2aa")) mustBe true
    }

    "return false when postcodes differ after normalization" in {
      utils.samePostcode(Some("EH1 1AA"), Some("EH1 2AA")) mustBe false
    }

    "treat None as empty string and compare accordingly" in {
      utils.samePostcode(None, None) mustBe true
      utils.samePostcode(None, Some("")) mustBe true
      utils.samePostcode(Some("W1A1HQ"), None) mustBe false
    }
  }

  "normCountry" must {
    "uppercase and strip all whitespace" in {
      utils.normCountry(Some("  Scotland ")) mustBe "SCOTLAND"
      utils.normCountry(Some("United   Kingdom")) mustBe "UNITEDKINGDOM"
      utils.normCountry(Some(" GB-ENG ")) mustBe "GB-ENG".toUpperCase.replaceAll("\\s+", "")
    }

    "return empty string for None" in {
      utils.normCountry(None) mustBe ""
    }
  }

  "normCountryFromChoice" must {
    "normalize from InternationalAddressChoiceDto" in {
      utils.normCountryFromChoice(Some(InternationalAddressChoiceDto.England)) mustBe "ENGLAND"
      utils.normCountryFromChoice(Some(InternationalAddressChoiceDto.Scotland)) mustBe "SCOTLAND"
      utils.normCountryFromChoice(Some(InternationalAddressChoiceDto.OutsideUK)) mustBe "OUTSIDEUK"
    }

    "return empty string for None" in {
      utils.normCountryFromChoice(None) mustBe ""
    }
  }

  "isCrossBorderScotland" must {
    "return true when exactly one side is Scotland (XOR)" in {
      utils.isCrossBorderScotland("SCOTLAND", "ENGLAND") mustBe true
      utils.isCrossBorderScotland("ENGLAND", "SCOTLAND") mustBe true
      utils.isCrossBorderScotland("", "SCOTLAND") mustBe true
      utils.isCrossBorderScotland("SCOTLAND", "") mustBe true
    }

    "return false when both are Scotland or both are not Scotland" in {
      utils.isCrossBorderScotland("SCOTLAND", "SCOTLAND") mustBe false
      utils.isCrossBorderScotland("ENGLAND", "WALES") mustBe false
      utils.isCrossBorderScotland("", "") mustBe false
    }
  }
}
