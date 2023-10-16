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

class SessionManagementControllerSpec extends BaseSpec {

  trait LocalSetup {
    val controller: SessionManagementController = app.injector.instanceOf[SessionManagementController]
  }

  "SessionManagementController.keepAlive" should {
    "return 200" in new LocalSetup {
      val result: Future[Result] = controller.keepAlive(FakeRequest("GET", ""))

      status(result) mustBe OK
    }
  }

  "SessionManagementController.timeOut" should {
    "return 303" in new LocalSetup {
      val result: Future[Result] = controller.timeOut()(FakeRequest("GET", ""))

      status(result) mustBe SEE_OTHER

    }

    "redirect to the session timeout page" in new LocalSetup {
      val result: Future[Result] = controller.timeOut()(FakeRequest("GET", ""))

      redirectLocation(result).getOrElse("Unable to complete") mustBe routes.PublicController.sessionTimeout.url
    }

    "clear the session upon redirect" in new LocalSetup {
      val result: Future[Result] = controller.timeOut()(FakeRequest("GET", "").withSession("test" -> "session"))

      session(result) mustBe empty
    }
  }

}
