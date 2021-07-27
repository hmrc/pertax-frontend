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

package util

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.http.{GatewayTimeoutException, HttpGet}
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}

import scala.concurrent.Future

class EnhancedPartialRetrieverSpec extends BaseSpec {

  lazy implicit val fakeRequest = FakeRequest("", "")

  trait LocalSetup {

    val metricId = "load-partial"

    def simulateCallFailed: Boolean

    def returnPartial: HtmlPartial

    lazy val (epr, metrics, timer) = {

      val timer = mock[Timer.Context]

      val epr = new EnhancedPartialRetriever(injected[HeaderCarrierForPartialsConverter]) with HasMetrics {

        override val http: HttpGet = mock[HttpGet]
        if (simulateCallFailed)
          when(http.GET[HtmlPartial](any(), any(), any())(any(), any(), any())) thenReturn Future.failed(
            new GatewayTimeoutException("Gateway timeout")
          )
        else
          when(http.GET[HtmlPartial](any(), any(), any())(any(), any(), any())) thenReturn Future.successful(
            returnPartial
          )

        override val metrics: Metrics = mock[Metrics]
        override val metricsOperator: MetricsOperator = mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
      }

      (epr, epr.metricsOperator, timer)
    }

  }

  "Calling EnhancedPartialRetriever.loadPartial" must {

    "return a successful partial and log the right metrics" in new LocalSetup {

      override val simulateCallFailed = false
      override val returnPartial = HtmlPartial.Success.apply(Some("my title"), Html("my body content"))

      epr.loadPartial("/").futureValue mustBe returnPartial

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(metrics, times(0)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()

    }

    "return a failed partial and log the right metrics" in new LocalSetup {

      override val simulateCallFailed = false
      override val returnPartial = HtmlPartial.Failure(Some(404), "Not Found")

      epr.loadPartial("/").futureValue mustBe returnPartial

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(0)).incrementSuccessCounter(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "when the call to the service fails log the right metrics" in new LocalSetup {

      override val simulateCallFailed = true
      override def returnPartial = ???

      epr.loadPartial("/").futureValue

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(0)).incrementSuccessCounter(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }
}
