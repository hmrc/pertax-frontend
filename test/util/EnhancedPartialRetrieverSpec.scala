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

package util

import com.codahale.metrics.Timer
import com.github.tomakehurst.wiremock.client.WireMock.{get, notFound, ok, serverError, urlEqualTo}
import metrics.{Metrics, MetricsImpl}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.twirl.api.Html
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}

class EnhancedPartialRetrieverSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  lazy implicit val fakeRequest = FakeRequest("", "")

  val mockMetrics: MetricsImpl = mock[MetricsImpl]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMetrics)
  }

  server.start()
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[MetricsImpl].toInstance(mockMetrics)
    )
    .configure(
      "microservice.services.contact-frontend.port" -> server.port(),
      "metrics.enabled"                             -> false,
      "auditing.enabled"                            -> false,
      "auditing.traceRequests"                      -> false
    )
    .build()

  val sut = injected[EnhancedPartialRetriever]

  "Calling EnhancedPartialRetriever.loadPartial" must {

    "return a successful partial and log the right metrics" in {

      val returnPartial: HtmlPartial = HtmlPartial.Success.apply(None, Html("my body content"))
      val url = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(ok("my body content"))
      )
      when(mockMetrics.startTimer(any())).thenReturn(mock[Timer.Context])
      when(mockMetrics.incrementSuccessCounter(any())).thenAnswer(_ => None)
      when(mockMetrics.incrementFailedCounter(any())).thenAnswer(_ => None)

      sut.loadPartial(url).futureValue mustBe returnPartial
      verify(mockMetrics, times(1)).startTimer(any())
      verify(mockMetrics, times(0)).incrementFailedCounter(any())
      verify(mockMetrics, times(1)).incrementSuccessCounter(any())
    }

    "return a failed partial and log the right metrics" in {

      val returnPartial: HtmlPartial = HtmlPartial.Failure(Some(404), "Not Found")
      val url = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(notFound.withBody("Not Found"))
      )

      when(mockMetrics.startTimer(any())).thenReturn(mock[Timer.Context])
      when(mockMetrics.incrementSuccessCounter(any())).thenAnswer(_ => None)
      when(mockMetrics.incrementFailedCounter(any())).thenAnswer(_ => None)

      sut.loadPartial(url).futureValue mustBe returnPartial
      verify(mockMetrics, times(1)).startTimer(any())
      verify(mockMetrics, times(1)).incrementFailedCounter(any())
      verify(mockMetrics, times(0)).incrementSuccessCounter(any())
    }

    "when the call to the service fails log the right metrics" in {

      val returnPartial: HtmlPartial = HtmlPartial.Failure(Some(500), "Error")
      val url = s"http://localhost:${server.port()}/"
      server.stubFor(
        get(urlEqualTo("/")).willReturn(serverError.withBody("Error"))
      )

      when(mockMetrics.startTimer(any())).thenReturn(mock[Timer.Context])
      when(mockMetrics.incrementSuccessCounter(any())).thenAnswer(_ => None)
      when(mockMetrics.incrementFailedCounter(any())).thenAnswer(_ => None)

      sut.loadPartial(url).futureValue mustBe returnPartial
      verify(mockMetrics, times(1)).startTimer(any())
      verify(mockMetrics, times(1)).incrementFailedCounter(any())
      verify(mockMetrics, times(0)).incrementSuccessCounter(any())
    }
  }
}
