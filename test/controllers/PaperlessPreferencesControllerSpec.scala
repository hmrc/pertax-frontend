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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction, WithBreadcrumbAction}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.partials.PreferencesFrontendPartialService
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util._

import scala.concurrent.{ExecutionContext, Future}

class PaperlessPreferencesControllerSpec extends BaseSpec with MockitoSugar {
  import BetterOptionValues._

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  val mockPreferencesFrontendPartialService = mock[PreferencesFrontendPartialService]
  val mockAuthJourney = mock[AuthJourney]

  def controller: PaperlessPreferencesController =
    new PaperlessPreferencesController(
      mockPreferencesFrontendPartialService,
      mockAuthJourney,
      injected[WithActiveTabAction],
      injected[WithBreadcrumbAction],
      injected[MessagesControllerComponents],
      injected[Tools]
    )(mock[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer], injected[ExecutionContext]) {}

  "Calling PaperlessPreferencesController.managePreferences" should {
    "Redirect to  preferences-frontend manage paperless url when a user is logged in using GG" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val r = controller.managePreferences(FakeRequest())
      status(r) shouldBe SEE_OTHER

      val redirectUrl = redirectLocation(r).getValue
      val configDecorator = app.injector.instanceOf[ConfigDecorator]
      redirectUrl should include regex s"${configDecorator.preferencesFrontendService}/paperless/check-settings\\?returnUrl=.*\\&returnLinkText=.*"
    }

    "Return 400 for Verify users" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            ))
      })

      val r = controller.managePreferences(FakeRequest())
      status(r) shouldBe BAD_REQUEST
    }
  }
}
