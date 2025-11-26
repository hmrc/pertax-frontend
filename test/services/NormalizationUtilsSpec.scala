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

  "postcodesMatch" must {

    "return true when postcodes are equal ignoring spaces and case" in {
      utils.postcodesMatch(Some("EH1 1AA"), Some("eh11aa")) mustBe true
      utils.postcodesMatch(Some(" SW1A  2AA "), Some("sw1a2aa")) mustBe true
    }

    "return false when postcodes differ after normalisation" in {
      utils.postcodesMatch(Some("EH1 1AA"), Some("EH1 2AA")) mustBe false
    }

    "treat None as empty string for comparison" in {
      utils.postcodesMatch(None, None) mustBe true
      utils.postcodesMatch(None, Some("")) mustBe true
      utils.postcodesMatch(Some("W1A1HQ"), None) mustBe false
    }
  }

  "normalizeCountryName" must {

    "uppercase and strip all whitespace" in {
      utils.normalizeCountryName(Some("  Scotland ")) mustBe "SCOTLAND"
      utils.normalizeCountryName(Some("United   Kingdom")) mustBe "UNITEDKINGDOM"
      utils.normalizeCountryName(Some(" GB-ENG ")) mustBe "GB-ENG".toUpperCase.replaceAll("\\s+", "")
    }

    "return empty string for None" in {
      utils.normalizeCountryName(None) mustBe ""
    }
  }

  "movedAcrossScottishBorder" must {

    "return true when exactly one side is Scotland" in {
      utils.movedAcrossScottishBorder("SCOTLAND", "ENGLAND") mustBe true
      utils.movedAcrossScottishBorder("ENGLAND", "SCOTLAND") mustBe true
      utils.movedAcrossScottishBorder("", "SCOTLAND") mustBe true
      utils.movedAcrossScottishBorder("SCOTLAND", "") mustBe true
    }

    "return false when both are Scotland or both are not Scotland" in {
      utils.movedAcrossScottishBorder("SCOTLAND", "SCOTLAND") mustBe false
      utils.movedAcrossScottishBorder("ENGLAND", "WALES") mustBe false
      utils.movedAcrossScottishBorder("", "") mustBe false
    }
  }

  "isUkCountry" must {

    "return true for recognised UK territories" in {
      utils.isUkCountry("UNITEDKINGDOM") mustBe true
      utils.isUkCountry("ENGLAND") mustBe true
      utils.isUkCountry("SCOTLAND") mustBe true
      utils.isUkCountry("WALES") mustBe true
      utils.isUkCountry("CYMRU") mustBe true
      utils.isUkCountry("NORTHERNIRELAND") mustBe true
    }

    "return false for anything else" in {
      utils.isUkCountry("FRANCE") mustBe false
      utils.isUkCountry("IRELAND") mustBe false
      utils.isUkCountry("") mustBe false
    }
  }

  "isNonUkCountry" must {

    "be the negation of isUkCountry" in {
      utils.isNonUkCountry("UNITEDKINGDOM") mustBe false
      utils.isNonUkCountry("SCOTLAND") mustBe false
      utils.isNonUkCountry("FRANCE") mustBe true
      utils.isNonUkCountry("") mustBe true
    }
  }
}
