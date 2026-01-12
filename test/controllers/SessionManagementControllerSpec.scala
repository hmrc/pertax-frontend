/*
 * Copyright 2025 HM Revenue & Customs
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
import controllers.interstitials.{InterstitialController, MtdAdvertInterstitialController}
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.http.SessionKeys

import scala.concurrent.Future

class SessionManagementControllerSpec extends BaseSpec {

  val mockInterstitialController: InterstitialController                   = mock[InterstitialController]
  val mockMtdAdvertInterstitialController: MtdAdvertInterstitialController = mock[MtdAdvertInterstitialController]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[MtdAdvertInterstitialController].toInstance(mockMtdAdvertInterstitialController),
      bind[AuthJourney].toInstance(mockAuthJourney)
    )
    .build()

  trait LocalSetup {
    val controller: SessionManagementController = app.injector.instanceOf[SessionManagementController]
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthJourney, mockInterstitialController, mockMtdAdvertInterstitialController)

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(buildUserRequest(request = request))
    })
  }

  "SessionManagementController.keepAlive" should {
    "return 200" in new LocalSetup {
      val result: Future[Result] =
        controller.keepAlive(
          FakeRequest("GET", "").withSession(SessionKeys.sessionId -> "test-session-id")
        )

      status(result) mustBe OK
    }
  }

  "SessionManagementController.timeOut" should {
    "return 303 and redirect to BAS Gateway sign-out" in new LocalSetup {

      val result: Future[Result] =
        controller.timeOut()(FakeRequest("GET", "").withSession(SessionKeys.sessionId -> "test-session-id"))

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        "http://localhost:9553/bas-gateway/sign-out-without-state?continue=http://localhost:9514/feedback/PERTAX"
      )
    }
  }

}
