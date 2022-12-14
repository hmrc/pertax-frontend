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
import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, serverError, urlEqualTo}
import connectors.EnhancedPartialRetriever
import controllers.auth.requests.UserRequest
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import services.partials.MessageFrontendService
import testUtils.Fixtures.buildFakeRequestWithAuth
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}

import scala.concurrent.Future

class MessageFrontendServiceSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  val mockHeaderCarrierForPartialsConverter: HeaderCarrierForPartialsConverter = mock[HeaderCarrierForPartialsConverter]
  val mockEnhancedPartialRetriever: EnhancedPartialRetriever                   = mock[EnhancedPartialRetriever]

  server.start()
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[EnhancedPartialRetriever].toInstance(mockEnhancedPartialRetriever)
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
      server.stubFor(
        get(urlEqualTo("/messages/count?read=No")).willReturn(serverError)
      )

      messageCount.futureValue mustBe None
    }

    "return None unread messages when http client does not return a usable response" in {
      server.stubFor(
        get(urlEqualTo("/messages/count?read=No")).willReturn(ok(Json.obj("testInvalid" -> "testInvalid").toString))
      )

      messageCount.futureValue mustBe None
    }

    "return 10 unread messages" in {
      server.stubFor(
        get(urlEqualTo("/messages/count?read=No")).willReturn(ok("""{"count": 10}"""))
      )

      messageCount.futureValue mustBe Some(10)
    }
  }
}
