/*
 * Copyright 2017 HM Revenue & Customs
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

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.twirl.api.Html
import services.http.WsAllMethods
import services.partials.MessagePartialService
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.partials.HtmlPartial
import util.BaseSpec
import util.Fixtures._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessagePartialServiceSpec extends BaseSpec {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(bind[WsAllMethods].toInstance(MockitoSugar.mock[WsAllMethods]))
    .build()


  override def beforeEach: Unit = {
    reset(injected[WsAllMethods])
  }

  trait LocalSetup {
  
    val partialService = injected[MessagePartialService]
  }

  "Calling MessagePartialService" should {

   "return message partial for list of messages" in new LocalSetup {

      when(partialService.http.GET[HtmlPartial](any())(any(),any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Title"), Html("<title/>")))

      partialService.getMessageListPartial(buildFakeRequestWithAuth("GET")).map(p =>
        p shouldBe "<title/>"
      )
      verify(partialService.http, times(1)).GET[Html](any())(any(),any())
    }

    "return message partial for message details" in new LocalSetup {

      when(partialService.http.GET[HtmlPartial](any())(any(),any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(Some("Test%20Title"), Html("Test Response String")))

      partialService.getMessageDetailPartial("")(buildFakeRequestWithAuth("GET")).map(p=>
        p shouldBe HtmlPartial.Success(Some("Test%20Title"), Html("Test Response String"))
      )
      verify(partialService.http, times(1)).GET[HttpResponse](any())(any(),any())
    }

    "return message inbox link partial" in new LocalSetup {

      when(partialService.http.GET[HtmlPartial](any())(any(),any())) thenReturn
        Future.successful[HtmlPartial](HtmlPartial.Success(None, Html("link to messages")))

      partialService.getMessageInboxLinkPartial(buildFakeRequestWithAuth("GET")).map(p=>
        p shouldBe HtmlPartial.Success(None, Html("link to messages"))
      )
      verify(partialService.http, times(1)).GET[HttpResponse](any())(any(),any())
    }

  }

}
