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

package controllers

import play.api.test.FakeRequest
import play.api.test.Helpers._
import util.BaseSpec

class LanguageControllerSpec extends BaseSpec {

  trait LocalSetup {
    val langSwitchController = app.injector.instanceOf[LanguageSwitchController]
  }

  "Calling LanguageController.enGb" should {
    "change the language to English and return 303" in new LocalSetup {
      val request = langSwitchController.enGb()(FakeRequest("GET", ""))
      cookies(request).get("PLAY_LANG").get.value shouldBe "en"
      status(request) shouldBe SEE_OTHER
    }
  }

  "Calling LanguageController.cyGb" should {
    "change the language to Welsh and return 303" in new LocalSetup {
      val request = langSwitchController.cyGb()(FakeRequest("GET", ""))
      cookies(request).get("PLAY_LANG").get.value shouldBe "cy"
      status(request) shouldBe SEE_OTHER
    }
  }
}
