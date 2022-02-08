/*
 * Copyright 2022 HM Revenue & Customs
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
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.controllershelpers.{HomeCardGenerator, HomePageCachingHelper}
import models.NonFilerSelfAssessmentUser
import org.mockito.Mockito.when
import play.api.http.Status.OK
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec}
import views.html.HomeView
import views.html.personaldetails.CheckYourAddressInterruptView

import scala.concurrent.Future

class RlsControllerSpec extends BaseSpec {

  val mockAuthJourney = mock[AuthJourney]

  def controller: RlsController =
    new RlsController(
      mockAuthJourney,
      injected[WithActiveTabAction],
      injected[MessagesControllerComponents],
      injected[CheckYourAddressInterruptView]
    )(injected[ConfigDecorator], injected[TemplateRenderer], ec)

  "rlsInterruptOnPageLoad" must {
    "return okay and CheckYourAddressInterruptView when called" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())
      status(r) mustBe OK

    }

    "return a 200 status when accessing index page with good nino and a non sa User" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            )
          )
      })

      val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())
      status(r) mustBe OK
    }
  }

  "return a 200 status when accessing index page with a nino that does not map to any personal details in citizen-details" in {

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = request)
        )
    })

    val r: Future[Result] = controller.rlsInterruptOnPageLoad()(FakeRequest())
    status(r) mustBe OK
  }
}
