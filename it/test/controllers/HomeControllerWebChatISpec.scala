/*
 * Copyright 2024 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Application
import play.api.http.Status._
import play.api.inject.bind
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, status => httpStatus, writeableOf_AnyContentAsEmpty}
import play.twirl.api.Html
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.webchat.client.WebChatClient

import java.util.UUID
import scala.concurrent.Future

class HomeControllerWebChatISpec extends IntegrationSpec {

  private val mockWebChatClient = mock[WebChatClient]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[WebChatClient].toInstance(mockWebChatClient)
    )
    .build()

  val url = s"/personal-account"

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()

    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details?cached=true"))
        .willReturn(
          ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino))
        )
    )
    when(mockWebChatClient.loadRequiredElements()(any())).thenReturn(Some(Html("loadRequiredElements")))
    when(mockWebChatClient.loadHMRCChatSkinElement(any(), any())(any()))
      .thenReturn(Some(Html("loadHMRCChatSkinElement")))
  }

  "personal account page" must {
    "show the webchat" in {
      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result) must include("loadRequiredElements")
      contentAsString(result) must include("loadHMRCChatSkinElement")
    }
  }

  "other page" must {
    "not show the webchat always" in {
      val result: Future[Result] = route(
        app,
        FakeRequest(GET, "/personal-account/profile-and-settings")
          .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      ).get
      httpStatus(result) mustBe OK
      contentAsString(result) mustNot include("loadRequiredElements")
      contentAsString(result) mustNot include("loadHMRCChatSkinElement")
    }
  }
}
