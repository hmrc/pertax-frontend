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

import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction, WithBreadcrumbAction}
import error.LocalErrorHandler
import models.{ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.mvc.{ActionBuilder, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, UserDetailsService}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageControllerSpec extends BaseSpec with MockitoSugar {

  override def beforeEach: Unit =
    reset(mockMessageFrontendService, mock[CitizenDetailsService])

  val mockAuthJourney = mock[AuthJourney]
  val mockMessageFrontendService = mock[MessageFrontendService]

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  def controller: MessageController =
    new MessageController(
      injected[MessagesApi],
      mockMessageFrontendService,
      mockAuthJourney,
      injected[WithActiveTabAction],
      injected[WithBreadcrumbAction])(mock[LocalPartialRetriever], configDecorator, injected[TemplateRenderer]) {
      when(mockMessageFrontendService.getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }
    }

  "Calling MessageController.messageList" should {
    "call messages and return 200 when called by a high GG user" in {

      when(mockAuthJourney.auth).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequest(
              Some(Fixtures.fakeNino),
              None,
              None,
              ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
              "GovernmentGateway",
              ConfidenceLevel.L200,
              None,
              None,
              None,
              None,
              request
            ))
      })

      when(controller.messageFrontendService.getMessageListPartial(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }

      val r = controller.messageList(FakeRequest())

      status(r) shouldBe OK
      verify(controller.messageFrontendService, times(1)).getMessageListPartial(any())
    }

    "return 401 for a verify user" in {

      when(mockAuthJourney.auth).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequest(
              Some(Fixtures.fakeNino),
              None,
              None,
              NonFilerSelfAssessmentUser,
              "Verify",
              ConfidenceLevel.L500,
              None,
              None,
              None,
              None,
              request
            ))
      })

      val r = controller.messageList(FakeRequest("GET", "/foo"))
      status(r) shouldBe UNAUTHORIZED

      verify(controller.messageFrontendService, times(0)).getMessageListPartial(any())
    }
  }

  "Calling MessageController.messageDetail" should {
    "call messages and return 200 when called by a GovernmentGateway user" in {

      when(mockAuthJourney.auth).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequest(
              Some(Fixtures.fakeNino),
              None,
              None,
              ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
              "GovernmentGateway",
              ConfidenceLevel.L200,
              None,
              None,
              None,
              None,
              request
            ))
      })

      when(controller.messageFrontendService.getMessageDetailPartial(any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(FakeRequest("GET", "/foo"))

      status(r) shouldBe OK
      verify(controller.messageFrontendService, times(1)).getMessageDetailPartial(any())(any())
    }

    "return 401 for a Verify user" in {

      when(mockAuthJourney.auth).thenReturn(new ActionBuilder[UserRequest] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            UserRequest(
              Some(Fixtures.fakeNino),
              None,
              None,
              ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
              "Verify",
              ConfidenceLevel.L500,
              None,
              None,
              None,
              None,
              request
            ))
      })

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(FakeRequest("GET", "/foo"))
      status(r) shouldBe UNAUTHORIZED
      verify(controller.messageFrontendService, times(0)).getMessageDetailPartial(any())(any())
    }
  }
}
