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

package controllers

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.BaseSpec

import scala.concurrent.Future

class LanguageControllerSpec extends BaseSpec {

  trait LocalSetup {
    val controller: LanguageSwitchController = app.injector.instanceOf[LanguageSwitchController]
  }

  "Calling LanguageController.enGb" must {
    "change the language to English and return 303" in new LocalSetup {
      val result: Future[Result] = controller.enGb()(FakeRequest("GET", ""))
      cookies(result).get("PLAY_LANG").get.value mustBe "en"
      status(result) mustBe SEE_OTHER
    }
  }

  "Calling LanguageController.cyGb" must {
    "change the language to Welsh and return 303" in new LocalSetup {
      val result: Future[Result] = controller.cyGb()(FakeRequest("GET", ""))
      cookies(result).get("PLAY_LANG").get.value mustBe "cy"
      status(result) mustBe SEE_OTHER
    }
  }
}
