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
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}

import scala.concurrent.Future
import org.mockito.Mockito.{times, verify, when}

class PaperlessPreferencesControllerSpec extends BaseSpec {
  import testUtils.BetterOptionValues._

  val mockInterstitialController: InterstitialController = mock[InterstitialController]
  override implicit lazy val app: Application            = localGuiceApplicationBuilder()
    .overrides(
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[AuthJourney].toInstance(mockAuthJourney)
    )
    .build()

  private lazy val controller: PaperlessPreferencesController = app.injector.instanceOf[PaperlessPreferencesController]

  "Calling PaperlessPreferencesController.managePreferences" must {
    "Redirect to  preferences-frontend manage paperless url when a user is logged in using GG" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val r = controller.managePreferences(FakeRequest())
      status(r) mustBe SEE_OTHER

      val redirectUrl = redirectLocation(r).getValue
      redirectUrl must include regex s"${config.preferencesFrontendService}/paperless/check-settings\\?returnUrl=.*\\&returnLinkText=.*"
    }
  }
}
