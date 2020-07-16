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
import org.jsoup.Jsoup
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.CitizenDetailsService
import services.partials.MessageFrontendService
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util._
import views.html.message.{MessageDetailView, MessageInboxView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MessageControllerSpec extends BaseSpec with MockitoSugar {

  override def beforeEach: Unit =
    reset(mockMessageFrontendService, mock[CitizenDetailsService])

  val mockAuthJourney = mock[AuthJourney]
  val mockMessageFrontendService = mock[MessageFrontendService]

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  implicit lazy val mat = app.materializer

  def controller: MessageController =
    new MessageController(
      mockMessageFrontendService,
      mockAuthJourney,
      injected[WithActiveTabAction],
      injected[WithBreadcrumbAction],
      injected[MessagesControllerComponents],
      injected[MessageInboxView],
      injected[MessageDetailView]
    )(mock[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer], injected[ExecutionContext]) {
      when(mockMessageFrontendService.getUnreadMessageCount(any())) thenReturn {
        Future.successful(None)
      }
    }

  "Calling MessageController.messageList" should {
    "call messages and return 200 when called by a high GG user" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request
            ))
      })

      when(mockMessageFrontendService.getMessageListPartial(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title>Message List</title>")))
      }

      val r = controller.messageList(FakeRequest())
      val body = contentAsString(r)

      status(r) shouldBe OK
      verify(mockMessageFrontendService, times(1)).getMessageListPartial(any())
      body should include("Message List")
    }
  }

  "Calling MessageController.messageDetail" should {
    "call messages and return 200 when called by a GovernmentGateway user" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request
            ))
      })

      when(mockMessageFrontendService.getMessageDetailPartial(any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(FakeRequest("GET", "/foo"))

      status(r) shouldBe OK
      verify(mockMessageFrontendService, times(1)).getMessageDetailPartial(any())(any())
    }

    "call messages and return 200 with no page title when called by a high GG user" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      when(mockMessageFrontendService.getMessageDetailPartial(any())(any())) thenReturn {
        Future(HtmlPartial.Success(None, Html("List")))
      }

      val r = controller.messageDetail("SOME_MESSAGE_TOKEN")(FakeRequest())
      val body = contentAsString(r)

      status(r) shouldBe OK
      verify(mockMessageFrontendService, times(1)).getMessageDetailPartial(any())(any())
      body should include("List")
    }

    "call messages and return 200 with default messages when called by a high GG user" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      when(mockMessageFrontendService.getMessageDetailPartial(any())(any())) thenReturn {
        Future(HtmlPartial.Failure(None, ""))
      }

      val r = controller.messageDetail("SOME_MESSAGE_TOKEN")(FakeRequest())
      val body = bodyOf(r)
      val doc = Jsoup.parse(body)

      Option(doc.getElementsByTag("article").text()).get should include(
        "Sorry, there has been a technical problem retrieving your message")

      status(r) shouldBe OK
      verify(mockMessageFrontendService, times(1)).getMessageDetailPartial(any())(any())
    }
  }
}
