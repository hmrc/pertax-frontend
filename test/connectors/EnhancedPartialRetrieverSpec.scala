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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.play.partials.HtmlPartial

class EnhancedPartialRetrieverSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  lazy implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")

  server.start()
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.contact-frontend.port" -> server.port(),
      "metrics.enabled"                             -> false,
      "auditing.enabled"                            -> false,
      "auditing.traceRequests"                      -> false
    )
    .build()

  val sut: EnhancedPartialRetriever = injected[EnhancedPartialRetriever]

  "Calling EnhancedPartialRetriever.loadPartial" must {

    "return a successful partial and log the right metrics" in {

      val returnPartial: HtmlPartial = HtmlPartial.Success.apply(None, Html("my body content"))
      val url                        = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(ok("my body content"))
      )
      sut.loadPartial(url).futureValue mustBe returnPartial
    }

    "return a failed partial and log the right metrics" in {

      val returnPartial: HtmlPartial = HtmlPartial.Failure(Some(404), "Not Found")
      val url                        = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(notFound.withBody("Not Found"))
      )
      sut.loadPartial(url).futureValue mustBe returnPartial
    }

    "when the call to the service fails log the right metrics" in {

      val returnPartial: HtmlPartial = HtmlPartial.Failure(Some(500), "Error")
      val url                        = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(serverError.withBody("Error"))
      )
      sut.loadPartial(url).futureValue mustBe returnPartial
    }
  }
}
