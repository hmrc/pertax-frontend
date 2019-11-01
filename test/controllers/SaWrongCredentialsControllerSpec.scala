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

import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.WrongCredentialsSelfAssessmentUser
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.mvc.{ActionBuilder, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures.buildFakeRequestWithAuth
import util.{BaseSpec, Fixtures, LocalPartialRetriever, UserRequestFixture}

import scala.concurrent.Future

class SaWrongCredentialsControllerSpec extends BaseSpec with MockitoSugar {

  val authJourney = mock[AuthJourney]

  when(authJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
    override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
      block(
        UserRequest(
          Some(Fixtures.fakeNino),
          None,
          None,
          WrongCredentialsSelfAssessmentUser(SaUtr("1111111111")),
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          None,
          None,
          None,
          None,
          request
        ))
  })

  val messagesApi = injected[MessagesApi]

  def controller =
    new SaWrongCredentialsController(messagesApi, authJourney)(
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
      val result = controller.doYouKnowOtherCredentials(FakeRequest())
      contentAsString(result) should include(messagesApi("title.do_you_know_user_id.h1"))
      contentAsString(result) should include("1111111111")
      status(result) shouldBe OK
    }
  }

  "processDoYouKnowOtherCredentials" should {
    "redirect to 'Sign in using Government Gateway' page when supplied with value Yes" in {
      when(authJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequestFixture
              .buildUserRequest(
                request = FakeRequest("POST", "")
                  .withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "true")
                  .asInstanceOf[Request[A]]
              )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processDoYouKnowOtherCredentials(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(config.signinGGUrl)
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in {
      when(authJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequestFixture
              .buildUserRequest(
                request = FakeRequest("POST", "")
                  .withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "false")
                  .asInstanceOf[Request[A]]
              )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processDoYouKnowOtherCredentials(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(routes.SaWrongCredentialsController.doYouKnowUserId().url)
    }

    "return a bad request when supplied no value" in {
      when(authJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequestFixture
              .buildUserRequest(
                request = FakeRequest("POST", "")
                  .asInstanceOf[Request[A]]
              )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processDoYouKnowOtherCredentials(FakeRequest())
      status(result) shouldBe BAD_REQUEST
    }
  }

  "processDoYouKnowUserId" should {
    "redirect to 'Sign in using Government Gateway' page when supplied with value Yes" in {
      when(authJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequestFixture
              .buildUserRequest(
                request = FakeRequest("POST", "")
                  .withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "true")
                  .asInstanceOf[Request[A]]
              )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processDoYouKnowUserId(FakeRequest())
      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(config.lostUserIdWithSa)
    }

    "redirect to 'You need to use the creds you've created' page when supplied with value No (false)" in {
      when(authJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequestFixture
              .buildUserRequest(
                request = FakeRequest("POST", "")
                  .withFormUrlEncodedBody("wrongCredentialsFormChoice" -> "false")
                  .asInstanceOf[Request[A]]
              )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processDoYouKnowUserId(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(await(result)) shouldBe Some(config.selfAssessmentContactUrl)
    }

    "return a bad request when supplied no value" in {
      when(authJourney.authWithSelfAssessment).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequestFixture
              .buildUserRequest(
                request = FakeRequest("POST", "")
                  .asInstanceOf[Request[A]]
              )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processDoYouKnowUserId(FakeRequest())
      status(result) shouldBe BAD_REQUEST
    }
  }
}
