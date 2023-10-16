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

import controllers.auth.FakeAuthJourney
import models.WrongCredentialsSelfAssessmentUser
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.BaseSpec
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import views.html.selfassessment._

class SaWrongCredentialsControllerSpec extends BaseSpec {

  val fakeAuthJourney = new FakeAuthJourney(
    WrongCredentialsSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr))
  )

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
    )(config)

  "processDoYouKnowOtherCredentials" must {
    "redirect to 'Sign in using Government Gateway' page when supplied with value Yes" in {

      val result = controller.processDoYouKnowOtherCredentials(
        FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "true")
      )
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SaWrongCredentialsController.signInAgain.url)
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in {

      val result = controller.processDoYouKnowOtherCredentials(
        FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "false")
      )

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SaWrongCredentialsController.doYouKnowUserId.url)
    }

    "return a bad request when supplied no value" in {

      val result = controller.processDoYouKnowOtherCredentials(FakeRequest("POST", ""))
      status(result) mustBe BAD_REQUEST
    }
  }

  "processDoYouKnowUserId" must {
    "redirect to 'Sign in using Government Gateway' page when supplied with value Yes" in {

      val result = controller.processDoYouKnowUserId(
        FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "true")
      )
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SaWrongCredentialsController.needToResetPassword.url)
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in {
      val result = controller.processDoYouKnowUserId(
        FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "false")
      )

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SaWrongCredentialsController.findYourUserId.url)
    }

    "return a bad request when supplied no value" in {
      val result = controller.processDoYouKnowUserId(FakeRequest("POST", ""))
      status(result) mustBe BAD_REQUEST
    }
  }

  "ggSignInUrl" must {
    "be the gg-sign in url" in {
      controller.ggSignInUrl mustBe "http://localhost:9553/bas-gateway/sign-in?continue_url=http://localhost:9232/personal-account&accountType=individual&origin=PERTAX"
    }
  }
}
