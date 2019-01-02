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

package models.dto

import util.BaseSpec

class AddressDtoSpec extends BaseSpec {


  "Posting the updateAddressForm" should {

    "bind an AddressDto correctly" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "",
        "line4" -> "",
        "line5" -> "",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
        },
        success => {
          success shouldBe AddressDto("Line 1", "Line 2", None, None, None, "AA1 1AA", None)
        }
      )
    }

    "bind an AddressDto correctly when postcode has no spaces" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "",
        "line4" -> "",
        "line5" -> "",
        "postcode" -> "AA11AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
        },
        success => {
          success shouldBe AddressDto("Line 1", "Line 2", None, None, None, "AA11AA", None)
        }
      )
    }

    "allow valid characters to be submitted" in {

      val formData = Map(
        "line1" -> " A-Za-z0-9&’'(),-./",
        "line2" -> "Line 2",
        "line3" -> "",
        "line4" -> "",
        "line5" -> "",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 0
        },
        success => {
          success shouldBe AddressDto(" A-Za-z0-9&’'(),-./", "Line 2", None, None, None, "AA1 1AA", None)
        }
      )
    }

    "return an error when no data is submitted in line 1" in {

      val formData = Map(
        "line1" -> "",
        "line2" -> "Line 2",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line1_required"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when no more than 35 characters entered in line 1" in {

      val formData = Map(
        "line1" -> "This is a string with more than thirty five characters",
        "line2" -> "Line 2",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line1_contains_more_than_35_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when invalid data is submitted in line 1" in {

      val formData = Map(
        "line1" -> "§",
        "line2" -> "Line 2",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line1_invalid_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when no data is submitted in line 2" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line2_required"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when more than 35 characters entered in line 2" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "This is a string with more than thirty five characters",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line2_contains_more_than_35_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when invalid data is submitted in line 2" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "±",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line2_invalid_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when more than 35 characters entered in line 3" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "This is a string with more than thirty five characters",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line3_contains_more_than_35_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when invalid data is submitted in line 3" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "±",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line3_invalid_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when data is entered in line 4 and not line 3" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "",
        "line4" -> "Line 4",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line3_required"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when more than 35 characters entered in line 4" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "Line 3",
        "line4" -> "This is a string with more than thirty five characters",
        "line5" -> "Line 5",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line4_contains_more_than_35_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when invalid data is submitted in line 4" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "Line 3",
        "line4" -> "§",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line4_invalid_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when data is entered in line 5 and not line 4" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "Line 3",
        "line4" -> "",
        "line5" -> "Line 5",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line4_required"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return two errors when data is entered in line 5 and not line 4 or line 3" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "",
        "line4" -> "",
        "line5" -> "Line 5",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 2
          formWithErrors.errors(0).message shouldBe "error.line3_required"
          formWithErrors.errors(1).message shouldBe "error.line4_required"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when more than 35 characters entered in line 5" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "Line 3",
        "line4" -> "Line 4",
        "line5" -> "This is a string with more than thirty five characters",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line5_contains_more_than_35_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when invalid data is submitted in line 5" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "Line 3",
        "line4" -> "Line 4",
        "line5" -> "§",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.line5_invalid_characters"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return two errors when invalid data is submitted in line 5 and line 4 is empty" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "line3" -> "Line 3",
        "line4" -> "",
        "line5" -> "§",
        "postcode" -> "AA1 1AA"
      )

      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 2
          formWithErrors.errors(1).message shouldBe "error.line5_invalid_characters"
          formWithErrors.errors(0).message shouldBe "error.line4_required"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when a postcode with invalid format is submitted in postcode field" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "postcode" -> "QN3 2E3"
      )
      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.enter_a_valid_uk_postcode"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }

    "return an error when a postcode with invalid characters is submitted in postcode field" in {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2",
        "postcode" -> "±±± §§§"
      )
      AddressDto.form.bind(formData).fold(
        formWithErrors => {
          formWithErrors.errors.length shouldBe 1
          formWithErrors.errors.head.message shouldBe "error.enter_a_valid_uk_postcode"
        },
        success => {
          fail("Form should give an error")
        }
      )
    }
  }
}
