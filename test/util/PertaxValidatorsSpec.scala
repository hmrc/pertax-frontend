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

package util

import play.api.data.Form
import play.api.data.Forms._
import util.PertaxValidators._

class PertaxValidatorsSpec extends BaseSpec {

  "Binding data to a simple address structure" must {

    trait LocalSetup

    "return 0 errors if line1 and line2 has content when using textIfFieldsHaveContent('line2') for line1's mapping" in new LocalSetup {

      val formData = Map(
        "line1" -> "Line 1",
        "line2" -> "Line 2"
      )

      case class SimpleAddress(line1: Option[String], line2: Option[String])

      val simpleAddressForm = Form(
        mapping(
          "line1" -> optionalTextIfFieldsHaveContent("line2"),
          "line2" -> optional(text)
        )(SimpleAddress.apply)(SimpleAddress.unapply)
      )

      val f = simpleAddressForm.bind(formData)
      f.copy(errors = f.errors.distinct)
        .fold(
          formWithErrors => fail("Form should give an error"),
          success =>
            success mustBe SimpleAddress(Some("Line 1"), Some("Line 2"))
        )
    }

    "return 1 error for line1 if line2 has content when using textIfFieldsHaveContent('line2') for line1's mapping" in new LocalSetup {

      val formData = Map(
        "line1" -> "",
        "line2" -> "Line 2"
      )

      case class SimpleAddress(line1: Option[String], line2: Option[String])

      val simpleAddressForm = Form(
        mapping(
          "line1" -> optionalTextIfFieldsHaveContent("line2"),
          "line2" -> optional(text)
        )(SimpleAddress.apply)(SimpleAddress.unapply)
      )

      val f = simpleAddressForm.bind(formData)
      f.copy(errors = f.errors.distinct)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 1
            formWithErrors.errors(0).key mustBe "line1"
            formWithErrors.errors(0).message mustBe "error.line1_required"
          },
          success => fail("Form should give an error")
        )
    }

    "return 2 errors for line1 + line2 if line1 and line2 contain no content when using textIfFieldsHaveContent('line2', 'line3') for line1's mapping and textIfFieldsHaveContent('line3') for line2's mapping" in new LocalSetup {

      val formData = Map(
        "line1" -> "",
        "line2" -> "",
        "line3" -> "Line 3"
      )

      case class SimpleAddress(
        line1: Option[String],
        line2: Option[String],
        line3: Option[String]
      )

      val simpleAddressForm = Form(
        mapping(
          "line1" -> optionalTextIfFieldsHaveContent("line2", "line3"),
          "line2" -> optionalTextIfFieldsHaveContent("line3"),
          "line3" -> optional(text)
        )(SimpleAddress.apply)(SimpleAddress.unapply)
      )

      val f = simpleAddressForm.bind(formData)
      f.copy(errors = f.errors.distinct)
        .fold(
          formWithErrors => {
            formWithErrors.errors.length mustBe 2
            formWithErrors.errors(0).key mustBe "line1"
            formWithErrors.errors(0).message mustBe "error.line1_required"
            formWithErrors.errors(1).key mustBe "line2"
            formWithErrors.errors(1).message mustBe "error.line2_required"
          },
          success => fail("Form should give an error")
        )
    }
  }

  "validateAddressLineCharacters" must {

    "return false when an illegal character is used" in {
      for (char <- """£!#$%*+:;<=>?@[\]^_"{|}~""")
        validateAddressLineCharacters(Some(char.toString)) mustBe false
    }

    "return false when illegal characters are used" in {
      validateAddressLineCharacters(
        Some("""45b Mühlendamm NE32 5RS""")
      ) mustBe false
    }

    "return true when no illegal characters are used" in {
      validateAddressLineCharacters(
        Some("""48/- Williams Park, Tyne & Wear NE32-5RS""")
      ) mustBe true
    }

  }
}
