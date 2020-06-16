/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.auth

import config.ConfigDecorator
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

class WithActiveSessionSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  class Harness(withActiveSession: WithActiveSession, cc: ControllerComponents) extends BaseController {
    override protected def controllerComponents: ControllerComponents = cc

    def onPageLoad(): Action[AnyContent] = withActiveSession { implicit request =>
      Ok(s"Success, session: ${request.session}")
    }
  }

  def cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]
  def configDecorator: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  implicit def ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  def action: WithActiveSession = new WithActiveSession(cc, configDecorator)

  def SUT = new Harness(action, cc)

  "WithActiveSession" must {

    "complete the given block" when {

      "a session is present in the request" in {

        val result = SUT.onPageLoad()(FakeRequest().withSession(("id", "123")))

        status(result) shouldBe OK
        contentAsString(result) should include("123")
      }
    }

    "redirect to the auth provider page" when {

      "the session is empty" in {

        val result = SUT.onPageLoad()(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(configDecorator.authProviderChoice)
      }
    }
  }
}
