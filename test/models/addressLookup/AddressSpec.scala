/*
 * Copyright 2020 HM Revenue & Customs
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

package models.addressLookup

import models.addresslookup.{Address, AddressRecord, Country}
import uk.gov.hmrc.play.test.UnitSpec

class classAddressModelSpec extends UnitSpec {
  val UK = Country("UK", "United Kingdom")

  "calling hasAddressLines" should {
    "return true where the address has at least one line" in {
      val addressLines = List("some line")
      Address(addressLines, None, None, "Some Postcode", UK, None).isValid shouldBe true
    }

    "return false where the address has no lines" in {
      val noAddressLines = List()
      Address(noAddressLines, None, None, "Some Postcode", UK, None).isValid shouldBe false
    }

    "An address with three lines and a town is valid" in {
      val a = Address(List("Line1", "Line2", "Line3"), Some("ATown"), Some("ACounty"), "FX1 1XX", UK, None)
      a.isValid shouldBe true
    }

    "An address with no lines and a town is not valid" in {
      val a = Address(Nil, Some("ATown"), Some("ACounty"), "FX1 1XX", UK, None)
      a.isValid shouldBe false
    }

    "An address with four lines and a town is not valid" in {
      val a = Address(List("a", "b", "c", "d"), Some("ATown"), Some("ACounty"), "FX1 1XX", UK, None)
      a.isValid shouldBe false
    }

    "An address with five lines and no town or county is not valid" in {
      val a = Address(List("a", "b", "c", "d", "e"), None, None, "FX1 1XX", UK, None)
      a.isValid shouldBe false
    }

    "Given a valid address in a record with a two-letter language,then the record should be valid" in {
      val a = Address(List("Line1", "Line2", "Line3"), Some("ATown"), Some("ACounty"), "FX1 1XX", UK, None)
      val ar = AddressRecord("abc123", a, "")
      ar.isValid shouldBe false
    }

    "Given a valid address in a record that does not have a two-letter language, then the record should be invalid" in {
      val a = Address(List("Line1", "Line2", "Line3"), Some("ATown"), Some("ACounty"), "FX1 1XX", UK, None)
      val ar = AddressRecord("abc123", a, "")
      ar.isValid shouldBe false
    }

  }
}
