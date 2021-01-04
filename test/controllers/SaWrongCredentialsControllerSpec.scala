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

package controllers

import controllers.auth.FakeAuthJourney
import models.WrongCredentialsSelfAssessmentUser
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, LocalPartialRetriever}
import views.html.selfassessment._

import scala.concurrent.ExecutionContext

class SaWrongCredentialsControllerSpec extends BaseSpec with MockitoSugar {

  val fakeAuthJourney = new FakeAuthJourney(
    WrongCredentialsSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)))

  def controller =
    new SaWrongCredentialsController(
      fakeAuthJourney,
      injected[MessagesControllerComponents],
      injected[SignedInWrongAccountView],
      injected[DoYouKnowOtherCredentialsView],
      injected[SignInAgainView],
      injected[DoYouKnowUserIdView],
      injected[NeedToResetPasswordView],
      injected[FindYourUserIdView]
    )(injected[LocalPartialRetriever], config, injected[TemplateRenderer], injected[ExecutionContext])

  "processDoYouKnowOtherCredentials" should {
    "redirect to 'Sign in using Government Gateway' page when supplied with value Yes" in {
      val request = FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "true")

      val result = controller.processDoYouKnowOtherCredentials(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(routes.SaWrongCredentialsController.signInAgain().url)
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in {
      val request = FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "false")

      val result = controller.processDoYouKnowOtherCredentials(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(routes.SaWrongCredentialsController.doYouKnowUserId().url)
    }

    "return a bad request when supplied no value" in {
      val request = FakeRequest("POST", "")

      val result = controller.processDoYouKnowOtherCredentials(request)
      status(result) shouldBe BAD_REQUEST
    }
  }

  "processDoYouKnowUserId" should {
    "redirect to 'Sign in using Government Gateway' page when supplied with value Yes" in {
      val request = FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "true")

      val result = controller.processDoYouKnowUserId(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(routes.SaWrongCredentialsController.needToResetPassword().url)
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in {
      val request = FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "false")
      val result = controller.processDoYouKnowUserId(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(routes.SaWrongCredentialsController.findYourUserId().url)
    }

    "return a bad request when supplied no value" in {
      val request = FakeRequest("POST", "")
      val result = controller.processDoYouKnowUserId(request)
      status(result) shouldBe BAD_REQUEST
    }
  }

  "ggSignInUrl" should {
    "be the gg-sign in url" in {
      controller.ggSignInUrl shouldBe "http://localhost:9553/bas-gateway/sign-in?continue_url=http://localhost:9232/personal-account&accountType=individual&origin=PERTAX"
    }
  }
}
