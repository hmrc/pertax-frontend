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

package services

import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, MessageCount}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.test.FakeRequest
import play.twirl.api.Html
import services.http.WsAllMethods
import services.partials.MessageFrontendService
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.partials.HtmlPartial
import util.BaseSpec
import util.Fixtures._

import scala.concurrent.Future

class MessageFrontendServiceSpec extends BaseSpec {

  lazy val userRequest = UserRequest(
    None,
    None,
    None,
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
    "SomeAuth",
    ConfidenceLevel.L200,
    None,
    None,
    None,
    None,
    FakeRequest("", "")
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(bind[WsAllMethods].toInstance(MockitoSugar.mock[WsAllMethods]))
    .build()

  val messageFrontendService: MessageFrontendService = injected[MessageFrontendService]

  override def beforeEach: Unit =
    reset(injected[WsAllMethods])

  "Calling getMessageListPartial" should {
    "return message partial for list of messages" in {

      when(messageFrontendService.http.GET[HtmlPartial](any())(any(), any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      await(messageFrontendService.getMessageListPartial(buildFakeRequestWithAuth("GET"))) shouldBe
        HtmlPartial.Success(Some("Title"), Html("<title/>"))

      verify(messageFrontendService.http, times(1)).GET[Html](any())(any(), any(), any())
    }
  }

  "Calling getMessageDetailPartial" should {
    "return message partial for message details" in {

      when(messageFrontendService.http.GET[HtmlPartial](any())(any(), any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Test%20Title"), Html("Test Response String")))

      await(messageFrontendService.getMessageDetailPartial("")(buildFakeRequestWithAuth("GET"))) shouldBe
        HtmlPartial.Success(Some("Test%20Title"), Html("Test Response String"))

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any())(any(), any(), any())
    }
  }

  "Calling getMessageInboxLinkPartial" should {
    "return message inbox link partial" in {

      when(messageFrontendService.http.GET[HtmlPartial](any())(any(), any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(None, Html("link to messages")))

      await(messageFrontendService.getMessageInboxLinkPartial(buildFakeRequestWithAuth("GET"))) shouldBe
        HtmlPartial.Success(None, Html("link to messages"))

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any())(any(), any(), any())
    }
  }

  "Calling getMessageCount" should {
    "return None unread messages when http client does not return a usable response" in {

      when(messageFrontendService.http.GET[Option[MessageCount]](any())(any(), any(), any())) thenReturn
        Future.successful[Option[MessageCount]](None)

      await(messageFrontendService.getUnreadMessageCount(buildFakeRequestWithAuth("GET"))) shouldBe None

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any())(any(), any(), any())
    }

    "return Some(0) unread messages when http client returns 0 unrread messages" in {

      when(messageFrontendService.http.GET[Option[MessageCount]](any())(any(), any(), any())) thenReturn
        Future.successful[Option[MessageCount]](Some(MessageCount(0)))

      await(messageFrontendService.getUnreadMessageCount(buildFakeRequestWithAuth("GET"))) shouldBe Some(0)

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any())(any(), any(), any())
    }

    "return Some(10) unread messages when http client returns 10 unrread messages" in {

      when(messageFrontendService.http.GET[Option[MessageCount]](any())(any(), any(), any())) thenReturn
        Future.successful[Option[MessageCount]](Some(MessageCount(10)))

      await(messageFrontendService.getUnreadMessageCount(buildFakeRequestWithAuth("GET"))) shouldBe Some(10)

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any())(any(), any(), any())
    }
  }
}
