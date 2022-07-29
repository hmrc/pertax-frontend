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

package services

import com.codahale.metrics.Timer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.{ok, serverError, urlEqualTo}
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import util.EnhancedPartialRetriever
import org.mockito.ArgumentMatchers._
import controllers.auth.requests.UserRequest
import metrics.MetricsImpl
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.mvc.AnyContentAsEmpty
import play.twirl.api.Html
import services.partials.MessageFrontendService
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}
import testUtils.Fixtures.buildFakeRequestWithAuth
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{BaseSpec, WireMockHelper}

import scala.concurrent.Future

class MessageFrontendServiceSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  val mockHeaderCarrierForPartialsConverter: HeaderCarrierForPartialsConverter = mock[HeaderCarrierForPartialsConverter]
  val mockEnhancedPartialRetriever: EnhancedPartialRetriever = mock[EnhancedPartialRetriever]
  val mockMetrics: MetricsImpl = mock[MetricsImpl]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMetrics)
  }

  server.start()
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[EnhancedPartialRetriever].toInstance(mockEnhancedPartialRetriever),
      bind[MetricsImpl].toInstance(mockMetrics)
    )
    .configure(
      "microservice.services.message-frontend.port" -> server.port(),
      "metrics.enabled"                             -> false,
      "auditing.enabled"                            -> false,
      "auditing.traceRequests"                      -> false
    )
    .build()

  lazy val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(
      request = FakeRequest("", "")
    )

  val messageFrontendService = injected[MessageFrontendService]

  "Calling getMessageListPartial" must {
    "return message partial for list of messages" in {
      val expected = HtmlPartial.Success.apply(None, Html("body"))
      when(mockEnhancedPartialRetriever.loadPartial(any())(any(), any())).thenReturn(
        Future.successful(expected)
      )

      val result = messageFrontendService.getMessageListPartial(FakeRequest()).futureValue

      result mustBe expected
    }
  }

  "Calling getMessageDetailPartial" must {
    "return message partial for message details" in {
      val expected = HtmlPartial.Success.apply(None, Html("body"))
      when(mockEnhancedPartialRetriever.loadPartial(any())(any(), any())).thenReturn(
        Future.successful(expected)
      )

      val partial = messageFrontendService.getMessageDetailPartial("abcd")(FakeRequest()).futureValue

      partial mustBe expected

    }
  }

  "Calling getMessageInboxLinkPartial" must {
    "return message inbox link partial" in {
      val expected = HtmlPartial.Success.apply(None, Html("body"))
      when(mockEnhancedPartialRetriever.loadPartial(any())(any(), any())).thenReturn(
        Future.successful(expected)
      )

      val partial = messageFrontendService.getMessageInboxLinkPartial(FakeRequest()).futureValue

      partial mustBe expected

    }
  }

  "Calling getMessageCount" must {
    def messageCount = messageFrontendService.getUnreadMessageCount(buildFakeRequestWithAuth("GET"))

    "return None unread messages when http client throws an exception" in {
      when(mockMetrics.startTimer(any())).thenReturn(mock[Timer.Context])
      when(mockMetrics.incrementSuccessCounter(any())).thenAnswer(_ => None)
      when(mockMetrics.incrementFailedCounter(any())).thenAnswer(_ => None)
      server.stubFor(
        get(urlEqualTo("/messages/count?read=No")).willReturn(serverError)
      )

      messageCount.futureValue mustBe None
      verify(mockMetrics, times(1)).startTimer(any())
      verify(mockMetrics, times(1)).incrementFailedCounter(any())
      verify(mockMetrics, times(0)).incrementSuccessCounter(any())
    }

    "return None unread messages when http client does not return a usable response" in {
      when(mockMetrics.startTimer(any())).thenReturn(mock[Timer.Context])
      when(mockMetrics.incrementSuccessCounter(any())).thenAnswer(_ => None)
      when(mockMetrics.incrementFailedCounter(any())).thenAnswer(_ => None)
      server.stubFor(
        get(urlEqualTo("/messages/count?read=No")).willReturn(ok("""Gibberish"""))
      )

      messageCount.futureValue mustBe None
      verify(mockMetrics, times(1)).startTimer(any())
      verify(mockMetrics, times(1)).incrementFailedCounter(any())
      verify(mockMetrics, times(0)).incrementSuccessCounter(any())
    }

    "return 10 unread messages" in {
      when(mockMetrics.startTimer(any())).thenReturn(mock[Timer.Context])
      when(mockMetrics.incrementSuccessCounter(any())).thenAnswer(_ => None)
      when(mockMetrics.incrementFailedCounter(any())).thenAnswer(_ => None)
      server.stubFor(
        get(urlEqualTo("/messages/count?read=No")).willReturn(ok("""{"count": 10}"""))
      )

      messageCount.futureValue mustBe Some(10)
      verify(mockMetrics, times(1)).startTimer(any())
      verify(mockMetrics, times(0)).incrementFailedCounter(any())
      verify(mockMetrics, times(1)).incrementSuccessCounter(any())
    }
  }
}
