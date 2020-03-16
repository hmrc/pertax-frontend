/*
 * Copyright 2020 HM Revenue & Customs
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
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.twirl.api.Html
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.http.{GatewayTimeoutException, HttpGet}
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.SessionCookieCrypto
import uk.gov.hmrc.play.partials.HtmlPartial

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnhancedPartialRetrieverSpec extends BaseSpec {

  trait LocalSetup {

    val metricId = "load-partial"

    def simulateCallFailed: Boolean

    def returnPartial: HtmlPartial

    lazy val (epr, metrics, timer) = {

      val timer = MockitoSugar.mock[Timer.Context]

      val epr = new EnhancedPartialRetriever(injected[SessionCookieCrypto]) with HasMetrics {

        override val http: HttpGet = MockitoSugar.mock[HttpGet]
        if (simulateCallFailed)
          when(http.GET[HtmlPartial](any())(any(), any(), any())) thenReturn Future.failed(
            new GatewayTimeoutException("Gateway timeout"))
        else when(http.GET[HtmlPartial](any())(any(), any(), any())) thenReturn Future.successful(returnPartial)

        override val metrics: Metrics = MockitoSugar.mock[Metrics]
        override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
      }

      (epr, epr.metricsOperator, timer)
    }

  }

  "Calling EnhancedPartialRetriever.loadPartial" should {

    "return a successful partial and log the right metrics" in new LocalSetup {

      override val simulateCallFailed = false
      override val returnPartial = HtmlPartial.Success.apply(Some("my title"), Html("my body content"))

      await(epr.loadPartial("/")) shouldBe returnPartial

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(metrics, times(0)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()

    }

    "return a failed partial and log the right metrics" in new LocalSetup {

      override val simulateCallFailed = false
      override val returnPartial = HtmlPartial.Failure(Some(404), "Not Found")

      await(epr.loadPartial("/")) shouldBe returnPartial

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(0)).incrementSuccessCounter(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "when the call to the service fails log the right metrics" in new LocalSetup {

      override val simulateCallFailed = true
      override def returnPartial = ???

      await(epr.loadPartial("/"))

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(0)).incrementSuccessCounter(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }
}
