/*
 * Copyright 2022 HM Revenue & Customs
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

import models.Address
import org.joda.time.LocalDate
import util.BaseSpec

class AddressDtoSpec extends BaseSpec {

  "Posting the updateAddressForm" must {

    "bind an AddressDto correctly" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "",
        "line4"    -> "",
        "line5"    -> "",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {},
          success => success mustBe AddressDto("Line 1", "Line 2", None, None, None, Some("AA1 1AA"), None, None)
        )
    }

    "bind an AddressDto correctly when postcode has no spaces" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "",
        "line4"    -> "",
        "line5"    -> "",
        "postcode" -> "AA11AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {},
          success => success mustBe AddressDto("Line 1", "Line 2", None, None, None, Some("AA11AA"), None, None)
        )
    }

    "allow valid characters to be submitted" in {

      val formData = Map(
        "line1"    -> "A-Za-z0-9&',-./",
        "line2"    -> "Line 2",
        "line3"    -> "",
        "line4"    -> "",
        "line5"    -> "",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => formWithErrors.errors.length mustBe 0,
          success =>
            success mustBe AddressDto(
              "A-Za-z0-9&',-./",
              "Line 2",
              None,
              None,
              None,
              Some("AA1 1AA"),
              None,
              None
            )
        )
    }

    "return an error when no data is submitted in line 1" in {

      val formData = Map(
        "line1"    -> "",
        "line2"    -> "Line 2",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line1_required"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when no more than 35 characters entered in line 1" in {

      val formData = Map(
        "line1"    -> "This is a string with more than thirty five characters",
        "line2"    -> "Line 2",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line1_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 1" in {

      val formData = Map(
        "line1"    -> "§",
        "line2"    -> "Line 2",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line1_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when no data is submitted in line 2" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line2_required"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when more than 35 characters entered in line 2" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "This is a string with more than thirty five characters",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line2_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 2" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "±",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line2_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when more than 35 characters entered in line 3" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "This is a string with more than thirty five characters",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line3_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 3" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "±",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line3_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "move line 4 data to line 3 if line 3 is empty" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "",
        "line4"    -> "Line 4",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          _ => fail("Form should not contain any errors"),
          success => {
            success
              .toAddress("Residential", LocalDate.now().minusDays(1))
              .line3
              .nonEmpty mustBe true

            success
              .toAddress("Residential", LocalDate.now().minusDays(1))
              .line4
              .isEmpty mustBe true
          }
        )
    }

    "return an error when more than 35 characters entered in line 4" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "Line 3",
        "line4"    -> "This is a string with more than thirty five characters",
        "line5"    -> "Line 5",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line4_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 4" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "Line 3",
        "line4"    -> "§",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line4_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "move line 5 data to line 4 if line 4 is empty" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "Line 3",
        "line4"    -> "",
        "line5"    -> "Line 5",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          _ => fail("Form should not contain any errors"),
          success => {
            success
              .toAddress("Residential", LocalDate.now().minusDays(1))
              .line4
              .nonEmpty mustBe true

            success
              .toAddress("Residential", LocalDate.now().minusDays(1))
              .line5
              .isEmpty mustBe true
          }
        )
    }

    "move line 5 data to line 3 if line 3 and 4 are empty" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "",
        "line4"    -> "",
        "line5"    -> "Line 5",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          _ => fail("Form should not contain any errors"),
          success => {
            success
              .toAddress("Residential", LocalDate.now().minusDays(1))
              .line3
              .nonEmpty mustBe true

            success
              .toAddress("Residential", LocalDate.now().minusDays(1))
              .line4
              .isEmpty mustBe true

            success
              .toAddress("Residential", LocalDate.now().minusDays(1))
              .line5
              .isEmpty mustBe true
          }
        )
    }

    "return an error when more than 35 characters entered in line 5" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "Line 3",
        "line4"    -> "Line 4",
        "line5"    -> "This is a string with more than thirty five characters",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line5_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 5" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "Line 3",
        "line4"    -> "Line 4",
        "line5"    -> "§",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line5_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return one error when invalid data is submitted in line 5 and line 4 is empty" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "line3"    -> "Line 3",
        "line4"    -> "",
        "line5"    -> "§",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line5_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when a postcode with invalid format is submitted in postcode field" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "postcode" -> "QN3 2E3"
      )
      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.enter_a_valid_uk_postcode"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when a postcode with invalid characters is submitted in postcode field" in {

      val formData = Map(
        "line1"    -> "Line 1",
        "line2"    -> "Line 2",
        "postcode" -> "±±± §§§"
      )
      AddressDto.ukForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.enter_a_valid_uk_postcode"
          },
          success => fail("Form should give an error")
        )
    }
  }

  "Posting the updateInternationalAddressForm" must {

    "bind an AddressDto correctly" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "Line 2",
        "line3"   -> "",
        "line4"   -> "",
        "line5"   -> "",
        "country" -> "Gibraltar",
        "status"  -> "0"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {},
          success => success mustBe AddressDto("Line 1", "Line 2", None, None, None, None, Some("Gibraltar"), None)
        )
    }

    "allow valid characters to be submitted" in {

      val formData = Map(
        "line1"   -> "A-Za-z0-9&',-./",
        "line2"   -> "Line 2",
        "line3"   -> "",
        "line4"   -> "",
        "line5"   -> "",
        "country" -> "Gibraltar",
        "status"  -> "0"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => formWithErrors.errors.length mustBe 0,
          success =>
            success mustBe AddressDto(
              "A-Za-z0-9&',-./",
              "Line 2",
              None,
              None,
              None,
              None,
              Some("Gibraltar"),
              None
            )
        )
    }

    "return an error when no data is submitted in line 1" in {

      val formData = Map(
        "line1"   -> "",
        "line2"   -> "Line 2",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line1_required"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when no more than 35 characters entered in line 1" in {

      val formData = Map(
        "line1"   -> "This is a string with more than thirty five characters",
        "line2"   -> "Line 2",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line1_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 1" in {

      val formData = Map(
        "line1"   -> "§",
        "line2"   -> "Line 2",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line1_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when no data is submitted in line 2" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line2_required"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when more than 35 characters entered in line 2" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "This is a string with more than thirty five characters",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line2_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 2" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "±",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line2_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when more than 35 characters entered in line 3" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "Line 2",
        "line3"   -> "This is a string with more than thirty five characters",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line3_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 3" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "Line 2",
        "line3"   -> "±",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line3_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when more than 35 characters entered in line 4" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "Line 2",
        "line3"   -> "Line 3",
        "line4"   -> "This is a string with more than thirty five characters",
        "line5"   -> "Line 5",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line4_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 4" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "Line 2",
        "line3"   -> "Line 3",
        "line4"   -> "§",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line4_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when more than 35 characters entered in line 5" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "Line 2",
        "line3"   -> "Line 3",
        "line4"   -> "Line 4",
        "line5"   -> "This is a string with more than thirty five characters",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line5_contains_more_than_35_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid data is submitted in line 5" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "Line 2",
        "line3"   -> "Line 3",
        "line4"   -> "Line 4",
        "line5"   -> "§",
        "country" -> "Gibraltar"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.line5_invalid_characters"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when invalid country is submitted" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "Line 2",
        "line3"   -> "Line 3",
        "line4"   -> "Line 4",
        "line5"   -> "Line 5",
        "country" -> "Country"
      )

      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.country_required"
          },
          success => fail("Form should give an error")
        )
    }

    "return an error when a country field is empty" in {

      val formData = Map(
        "line1"   -> "Line 1",
        "line2"   -> "Line 2",
        "country" -> ""
      )
      AddressDto.internationalForm
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.country_required"
          },
          success => fail("Form should give an error")
        )
    }
  }

  "Calling AddressDto.toList" must {

    "return address with postcode and not country" in {
      val addressDto =
        AddressDto("Line 1", "Line 2", Some("Line 3"), None, None, Some("AA1 1AA"), Some("UK"), None)

      addressDto.toList mustBe Seq("Line 1", "Line 2", "Line 3", "AA1 1AA")
    }
  }

  "Calling AddressDto.toListWithCountry" must {

    "return address with country and not postcode" in {
      val addressDto =
        AddressDto("Line 1", "Line 2", Some("Line 3"), None, None, Some("AA1 1AA"), Some("UK"), None)

      addressDto.toListWithCountry mustBe Seq("Line 1", "Line 2", "Line 3", "UK")
    }
  }

  "Calling AddressDto.toAddress" must {

    "return address with postcode and not country when postcode exists" in {
      val addressDto =
        AddressDto(
          "Line 1",
          "Line 2",
          Some("Line 3"),
          Some("Line 4"),
          Some("Line 5"),
          Some("AA1 1AA"),
          Some("UK"),
          None
        )
      val addressTye = "residential"
      val startDate = new LocalDate(2019, 1, 1)

      addressDto.toAddress(addressTye, startDate) mustBe Address(
        Some("Line 1"),
        Some("Line 2"),
        Some("Line 3"),
        Some("Line 4"),
        Some("Line 5"),
        Some("AA1 1AA"),
        None,
        Some(startDate),
        None,
        Some(addressTye),
        false
      )
    }

    "return address with country when postcode does not exist" in {
      val addressDto = AddressDto("Line 1", "Line 2", Some("Line 3"), None, None, None, Some("UK"), None)
      val addressTye = "residential"
      val startDate = new LocalDate(2019, 1, 1)

      addressDto.toAddress(addressTye, startDate) mustBe Address(
        Some("Line 1"),
        Some("Line 2"),
        Some("Line 3"),
        None,
        None,
        None,
        Some("UK"),
        Some(startDate),
        None,
        Some(addressTye),
        false
      )
    }
  }

  "Calling AddressDto.toAddress" must {

    "return formatted postcode when it contains 7 characters" in {
      val addressDto =
        AddressDto("Line 1", "Line 2", Some("Line 3"), None, None, Some("AA9A9AA"), Some("UK"), None)
      val addressTye = "residential"
      val startDate = new LocalDate(2019, 1, 1)

      addressDto.toAddress(addressTye, startDate) mustBe Address(
        Some("Line 1"),
        Some("Line 2"),
        Some("Line 3"),
        None,
        None,
        Some("AA9A 9AA"),
        None,
        Some(startDate),
        None,
        Some(addressTye),
        false
      )
    }

    "return formatted postcode when it contains 6 characters" in {
      val addressDto =
        AddressDto("Line 1", "Line 2", Some("Line 3"), None, None, Some("A9A9AA"), Some("UK"), None)
      val addressTye = "residential"
      val startDate = new LocalDate(2019, 1, 1)

      addressDto.toAddress(addressTye, startDate) mustBe Address(
        Some("Line 1"),
        Some("Line 2"),
        Some("Line 3"),
        None,
        None,
        Some("A9A 9AA"),
        None,
        Some(startDate),
        None,
        Some(addressTye),
        false
      )
    }

    "return formatted postcode when it contains 5 characters" in {
      val addressDto =
        AddressDto("Line 1", "Line 2", Some("Line 3"), None, None, Some("A99AA"), Some("UK"), None)
      val addressTye = "residential"
      val startDate = new LocalDate(2019, 1, 1)

      addressDto.toAddress(addressTye, startDate) mustBe Address(
        Some("Line 1"),
        Some("Line 2"),
        Some("Line 3"),
        None,
        None,
        Some("A9 9AA"),
        None,
        Some(startDate),
        None,
        Some(addressTye),
        false
      )
    }
  }

}
