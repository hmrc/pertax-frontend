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

import org.apache.pekko.stream.Materializer
import connectors.CitizenDetailsConnector
import controllers.auth.requests.UserRequest
import controllers.auth.WithBreadcrumbAction
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.partials.MessageFrontendService
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.play.partials.HtmlPartial
import views.html.message.{MessageDetailView, MessageInboxView}

import scala.concurrent.Future

class MessageControllerSpec extends BaseSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMessageFrontendService, mock[CitizenDetailsConnector])
  }

  val mockMessageFrontendService: MessageFrontendService = mock[MessageFrontendService]

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  implicit lazy val mat: Materializer = app.materializer

  def controller: MessageController =
    new MessageController(
      mockMessageFrontendService,
      mockAuthJourney,
      inject[WithBreadcrumbAction],
      inject[MessagesControllerComponents],
      inject[MessageInboxView],
      inject[MessageDetailView]
    )(config, ec)

  "Calling MessageController.messageList" must {
    "call messages and return 200 when called by a high GG user" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request
            )
          )
      })

      when(mockMessageFrontendService.getMessageListPartial(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title>Message List</title>")))
      }

      val r    = controller.messageList(FakeRequest())
      val body = contentAsString(r)

      status(r) mustBe OK
      verify(mockMessageFrontendService, times(1)).getMessageListPartial(any())
      body must include("Message List")
    }
  }

  "Calling MessageController.messageDetail" must {
    "call messages and return 200 when called by a GovernmentGateway user" in {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request
            )
          )
      })

      when(mockMessageFrontendService.getMessageDetailPartial(any())(any())) thenReturn {
        Future(HtmlPartial.Success(Some("Success"), Html("<title/>")))
      }

      val r = controller.messageDetail("SOME-MESSAGE-TOKEN")(FakeRequest("GET", "/foo"))

      status(r) mustBe OK
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

      val r    = controller.messageDetail("SOME_MESSAGE_TOKEN")(FakeRequest())
      val body = contentAsString(r)

      status(r) mustBe OK
      verify(mockMessageFrontendService, times(1)).getMessageDetailPartial(any())(any())
      body must include("List")
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

      val r    = controller.messageDetail("SOME_MESSAGE_TOKEN")(FakeRequest())
      val body = contentAsString(r)
      val doc  = Jsoup.parse(body)

      Option(doc.text()).get must include(
        "Sorry, there has been a technical problem retrieving your message"
      )

      status(r) mustBe OK
      verify(mockMessageFrontendService, times(1)).getMessageDetailPartial(any())(any())
    }
  }
}
