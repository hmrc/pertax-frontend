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

package controllers

import play.api.test.FakeRequest
import play.api.test.Helpers._
import util.BaseSpec

class SessionManagementControllerSpec extends BaseSpec {

  trait LocalSetup {
    val controller = app.injector.instanceOf[SessionManagementController]
  }

  "SessionManagementController.keepAlive" should {

    "return 200" in new LocalSetup {

      val result = controller.keepAlive()(FakeRequest("GET", ""))

      status(result) shouldBe OK
    }
  }

  "SessionManagementController.timeOut" should {

    "return 303" in new LocalSetup {

      val result = controller.timeOut()(FakeRequest("GET", ""))

      status(result) shouldBe SEE_OTHER

    }

    "redirect to the session timeout page" in new LocalSetup {

      val result = controller.timeOut()(FakeRequest("GET", ""))

      redirectLocation(result).getOrElse("Unable to complete") shouldBe routes.PublicController.sessionTimeout().url
    }

    "clear the session upon redirect" in new LocalSetup {

      val result = controller.timeOut()(FakeRequest("GET", "").withSession("test" -> "session"))

      session(result) shouldBe empty
    }
  }

}
