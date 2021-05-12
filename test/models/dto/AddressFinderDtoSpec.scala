/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.MustMatchers.convertToAnyMustWrapper
import util.BaseSpec

class AddressFinderDtoSpec extends BaseSpec {

  "Posting the postcodeLookup form" must {

    "bind an AddressFinderDto correctly when postcode and filter are valid" in {

      val formData = Map(
        "postcode" -> "AA1 1AA",
        "filter"   -> "6"
      )

      AddressFinderDto.form
        .bind(formData)
        .fold(
          formWithErrors => {},
          success => {
            success mustBe AddressFinderDto("AA1 1AA", Some("6"))
          }
        )
    }

    "return an error when postcode is invalid and filter is valid" in {

      val formData = Map(
        "postcode" -> "±±± §§§",
        "filter"   -> "6"
      )

      AddressFinderDto.form
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.enter_a_valid_uk_postcode"
          },
          success => {
            fail("Form should give an error")
          }
        )
    }

    "return an error when postcode is valid and filter is invalid" in {

      val formData = Map(
        "postcode" -> "AA1 1AA",
        "filter"   -> "§"
      )

      AddressFinderDto.form
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.enter_valid_characters"
          },
          success => {
            fail("Form should give an error")
          }
        )
    }

    "return two errors when postcode is invalid and filter is invalid" in {

      val formData = Map(
        "postcode" -> "±±± §§§",
        "filter"   -> "§"
      )

      AddressFinderDto.form
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 2
            formWithErrors.errors(0).message mustBe "error.enter_a_valid_uk_postcode"
            formWithErrors.errors(1).message mustBe "error.enter_valid_characters"
          },
          success => {
            fail("Form should give an error")
          }
        )
    }

    "bind correctly with valid postcode and no filter" in {

      val formData = Map(
        "postcode" -> "AA1 1AA"
      )

      AddressFinderDto.form
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 0
          },
          success => {
            success mustBe AddressFinderDto("AA1 1AA", None)
          }
        )
    }

    "return an error when no postcode is submitted" in {

      val formData = Map(
        "postcode" -> ""
      )

      AddressFinderDto.form
        .bind(formData)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors.head.message mustBe "error.enter_a_valid_uk_postcode"
          },
          success => {
            fail("Form should give an error")
          }
        )
    }
  }

}
