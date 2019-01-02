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

import config.ConfigDecorator
import models.PertaxContext
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.test.FakeRequest
import util.BaseSpec

class PertaxBaseControllerSpec extends BaseSpec {

  trait LocalSetup {

    def code: String
    lazy val c: PertaxBaseController = app.injector.instanceOf[InterstitialController]
    implicit lazy val messages = Messages(Lang(code), injected[MessagesApi])
    implicit val iCtx = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, injected[ConfigDecorator])
    lazy val ctx: PertaxContext = c.showingWarningIfWelsh { ctx =>
      ctx
    }
  }

  "Calling PertaxBaseController.isWelshLang" should {

    "return true when language code is 'cy'" in new LocalSetup {

      override val code = "cy"
      ctx.welshWarning shouldBe true
    }

    "return false when language code is 'en'" in new LocalSetup {

      override val code = "en"
      ctx.welshWarning shouldBe false
    }
  }
}
