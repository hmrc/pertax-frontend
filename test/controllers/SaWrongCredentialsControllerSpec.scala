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

import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.interstitials.InterstitialController
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}

import scala.concurrent.Future

class SaWrongCredentialsControllerSpec extends BaseSpec {

  val mockInterstitialController: InterstitialController = mock[InterstitialController]
  override implicit lazy val app: Application            = localGuiceApplicationBuilder()
    .overrides(
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[AuthJourney].toInstance(mockAuthJourney)
    )
    .build()

  private lazy val controller: SaWrongCredentialsController = app.injector.instanceOf[SaWrongCredentialsController]

  when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
    override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
      block(
        buildUserRequest(request = request)
      )
  })

  "processDoYouKnowOtherCredentials" must {
    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in {
      val result = controller.processDoYouKnowOtherCredentials()(
        FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "false")
      )
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SaWrongCredentialsController.doYouKnowUserId().url)
    }

    "return a bad request when supplied no value" in {
      val result = controller.processDoYouKnowOtherCredentials()(FakeRequest("POST", ""))
      status(result) mustBe BAD_REQUEST
    }
  }

  "processDoYouKnowUserId" must {

    "redirect to 'Sign in' page when supplied with value Yes" in {
      val result = controller.processDoYouKnowUserId()(
        FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "true")
      )
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SaWrongCredentialsController.needToResetPassword().url)
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in {
      val result = controller.processDoYouKnowUserId()(
        FakeRequest("POST", "").withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "false")
      )
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.SaWrongCredentialsController.findYourUserId().url)
    }

    "return a bad request when supplied no value" in {
      val result = controller.processDoYouKnowUserId()(FakeRequest("POST", ""))
      status(result) mustBe BAD_REQUEST
    }
  }

  "ggSignInUrl" must {
    "be the gg-sign in url" in {
      controller.ggSignInUrl mustBe "http://localhost:9553/bas-gateway/sign-in?continue_url=http://localhost:9232/personal-account&accountType=individual&origin=PERTAX"
    }
  }
}
