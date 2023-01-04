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

package controllers.bindable

import org.scalatest.{EitherValues, OptionValues}
import testUtils.BaseSpec
import controllers.bindable.Origin._

class OriginSpec extends BaseSpec with EitherValues with OptionValues {

  "Origin" should {

    "be valid" in {
      Origin("testing1").origin mustBe "testing1"
      Origin("Testing1").origin mustBe "Testing1"
      Origin("test-ing1").origin mustBe "test-ing1"
      Origin("tesA.ing1").origin mustBe "tesA.ing1"
      Origin(List.fill(100)('0').mkString).origin mustBe List.fill(100)('0').mkString
    }

    "be invalid" in {
      an[IllegalArgumentException] should be thrownBy Origin("withInvalidCharacters!")
      an[IllegalArgumentException] should be thrownBy Origin("with white spaces")
      an[IllegalArgumentException] should be thrownBy Origin("")
      an[IllegalArgumentException] should be thrownBy Origin(List.fill(101)('0').mkString)
    }
  }

  "Origin binder" should {

    "default when origin has invalid characters" in {
      queryBinder.bind("origin", Map("origin" -> Seq("!asdasd"))).value.right.value must be(Origin("unknown"))
    }

    "default when no origin supplied" in {
      queryBinder.bind("origin", Map("origin" -> Seq.empty)).value.right.value must be(Origin("unknown"))
    }

    "take the first when two origins supplied" in {
      queryBinder.bind("origin", Map("origin" -> Seq("origin1", "origin2"))).value.right.value must be(
        Origin("origin1")
      )
    }

    "create origin" in {
      queryBinder.bind("origin", Map("origin" -> Seq("theOrigin"))).value.right.value must be(Origin("theOrigin"))
    }

  }

  "Unbinding a continue URL" should {
    "return the value" in {
      queryBinder.unbind("origin", Origin("tax-account-router")) must be("origin=tax-account-router")
    }
  }

}
