/*
 * Copyright 2021 HM Revenue & Customs
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

import com.codahale.metrics.Timer.Context
import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import com.kenshoo.play.metrics.Metrics
import controllers.auth.requests.UserRequest
import models.MessageCount
import org.mockito.ArgumentMatchers.{any, _}
import org.mockito.Mockito._
import play.api.Application
import play.api.inject._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import services.partials.MessageFrontendService
import uk.gov.hmrc.http.{HttpException, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.partials.HtmlPartial
import util.BaseSpec
import util.Fixtures._
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.Future

class MessageFrontendServiceSpec extends BaseSpec {

  lazy val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(
      request = FakeRequest("", "")
    )

  val mockMetrics = mock[Metrics]
  val mockMetricRegistry = mock[MetricRegistry]
  val mockTimer = mock[Timer]
  val mockCounter = mock[Counter]
  val mockContext = mock[Context]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(bind[DefaultHttpClient].toInstance(mock[DefaultHttpClient]))
    .overrides(bind[Metrics].toInstance(mockMetrics))
    .build()

  val messageFrontendService: MessageFrontendService = injected[MessageFrontendService]

  override def beforeEach: Unit = reset(
    injected[DefaultHttpClient]
  )

  when(mockMetrics.defaultRegistry).thenReturn(mockMetricRegistry)

  when(mockMetricRegistry.timer(anyString())).thenReturn(mockTimer)
  when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter)

  when(mockTimer.time()).thenReturn(mockContext)

  "Calling getMessageListPartial" must {
    "return message partial for list of messages" in {

      when(messageFrontendService.http.GET[HtmlPartial](any(), any(), any())(any(), any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      val result = messageFrontendService.getMessageListPartial(FakeRequest()).futureValue

      result mustBe
        HtmlPartial.Success(Some("Title"), Html("<title/>"))

      verify(messageFrontendService.http, times(1)).GET[Html](any(), any(), any())(any(), any(), any())
    }
  }

  "Calling getMessageDetailPartial" must {
    "return message partial for message details" in {

      when(messageFrontendService.http.GET[HtmlPartial](any(), any(), any())(any(), any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Test%20Title"), Html("Test Response String")))

      val partial = messageFrontendService.getMessageDetailPartial("")(FakeRequest()).futureValue

      partial mustBe HtmlPartial.Success(Some("Test%20Title"), Html("Test Response String"))

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any(), any(), any())(any(), any(), any())
    }
  }

  "Calling getMessageInboxLinkPartial" must {
    "return message inbox link partial" in {

      when(messageFrontendService.http.GET[HtmlPartial](any(), any(), any())(any(), any(), any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(None, Html("link to messages")))

      val partial = messageFrontendService.getMessageInboxLinkPartial(FakeRequest()).futureValue

      partial mustBe HtmlPartial.Success(None, Html("link to messages"))

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any(), any(), any())(any(), any(), any())
    }
  }

  "Calling getMessageCount" must {
    def messageCount = messageFrontendService.getUnreadMessageCount(buildFakeRequestWithAuth("GET"))

    "return None unread messages when http client throws an exception" in {

      when(messageFrontendService.http.GET[Option[MessageCount]](any(), any(), any())(any(), any(), any())) thenReturn
        Future.failed(new HttpException("bad", 413))

      messageCount.futureValue mustBe None

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any(), any(), any())(any(), any(), any())
    }

    "return None unread messages when http client does not return a usable response" in {

      when(messageFrontendService.http.GET[Option[MessageCount]](any(), any(), any())(any(), any(), any())) thenReturn
        Future.successful[Option[MessageCount]](None)

      messageCount.futureValue mustBe None

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any(), any(), any())(any(), any(), any())
    }

    "return Some(0) unread messages when http client returns 0 unrread messages" in {

      when(messageFrontendService.http.GET[Option[MessageCount]](any(), any(), any())(any(), any(), any())) thenReturn
        Future.successful[Option[MessageCount]](Some(MessageCount(0)))

      messageCount.futureValue mustBe Some(0)

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any(), any(), any())(any(), any(), any())
    }

    "return Some(10) unread messages when http client returns 10 unrread messages" in {

      when(messageFrontendService.http.GET[Option[MessageCount]](any(), any(), any())(any(), any(), any())) thenReturn
        Future.successful[Option[MessageCount]](Some(MessageCount(10)))

      messageCount.futureValue mustBe Some(10)

      verify(messageFrontendService.http, times(1)).GET[HttpResponse](any(), any(), any())(any(), any(), any())
    }
  }
}
