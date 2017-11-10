/*
 * Copyright 2017 HM Revenue & Customs
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

import util.BaseSpec

class BindableSpec extends BaseSpec {

  trait LocalSetup {

  }

  "Calling originBinder.unbind" should {

    "return origin=PERTAX when key is origin and value is PERTAX" in {
      controllers.bindable.originBinder.unbind("origin", Origin("PERTAX")) shouldBe "origin=PERTAX"
    }
    "UrlEncode keys" in {
      controllers.bindable.originBinder.unbind("£", Origin("PERTAX")) shouldBe "%C2%A3=PERTAX"
    }
    "UrlEncode values" in {
      controllers.bindable.originBinder.unbind("origin", Origin("£")) shouldBe "origin=%C2%A3"
    }
  }

  "Calling originBinder.bind" should {

    "return an origin when called with a valid string" in new LocalSetup {
      controllers.bindable.originBinder.bind("origin", Map("origin" -> Seq("PERTAX"))) shouldBe Some(Right(Origin("PERTAX")))
    }
    "return None when called with an empty map" in new LocalSetup {
      controllers.bindable.originBinder.bind("origin", Map()) shouldBe None
    }
  }
}
