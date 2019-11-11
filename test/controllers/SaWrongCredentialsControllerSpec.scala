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

import controllers.auth.FakeAuthJourney
import models.WrongCredentialsSelfAssessmentUser
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, LocalPartialRetriever}

class SaWrongCredentialsControllerSpec extends BaseSpec with MockitoSugar {

  val fakeAuthJourney = new FakeAuthJourney(WrongCredentialsSelfAssessmentUser(SaUtr("1111111111")))

  val messagesApi = injected[MessagesApi]

  def controller =
    new SaWrongCredentialsController(messagesApi, fakeAuthJourney)(
      injected[LocalPartialRetriever],
      config,
      injected[TemplateRenderer])

  "landingPage" should {
    "render the signed-in-wrong-account page" in {
      val result = controller.landingPage(FakeRequest())
      contentAsString(result) should include(messagesApi("title.signed_in_wrong_account.h1"))
      status(result) shouldBe OK
    }
  }

  "doYouKnowOtherCredentials" should {
    "render the do-you-know-your-credentials page" in {
      val result = controller.doYouKnowOtherCredentials(FakeRequest())
      contentAsString(result) should include(messagesApi("title.do_you_know_other_credentials.h1"))
      status(result) shouldBe OK
    }
  }

  "doYouKnowUserId" should {
    "render the do-you-know-your-user-id page" in {
      val result = controller.doYouKnowUserId(FakeRequest())
      contentAsString(result) should include(messagesApi("title.do_you_know_user_id.h1"))
      status(result) shouldBe OK
    }
  }

  "signInAgain" should {
    "render the sign-in-again page" in {
      val result = controller.signInAgain(FakeRequest())
      contentAsString(result) should include(messagesApi("title.sign_in_again.h1"))
      status(result) shouldBe OK
    }
  }

  "needToResetPassword" should {
    "render the need-to-reset-password page" in {
      val result = controller.needToResetPassword(FakeRequest())
      val content = contentAsString(result)
      content should include("1111111111")
      content should include(messagesApi("title.reset_your_password.h1"))
      status(result) shouldBe OK
    }
  }

  "findYourUserId" should {
    "render the find-your-user-id page" in {
      val result = controller.findYourUserId(FakeRequest())
      val content = contentAsString(result)
      content should include("1111111111")
      content should include(messagesApi("title.find_your_user_id.h1"))
      status(result) shouldBe OK
    }
  }

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
}
