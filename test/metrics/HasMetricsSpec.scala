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

package metrics

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.{Metrics => KenshooMetrics}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{times, when}
import util.BaseSpec

class HasMetricsSpec extends BaseSpec {

  trait SetUp {
    class TestHasMetrics extends HasMetrics {
      val timer = mock[Timer.Context]
      val metricId = "test"

      override def metrics: KenshooMetrics = mock[KenshooMetrics]

      override val metricsOperator = mock[MetricsOperator]
      when(metricsOperator.startTimer(any())) thenReturn timer

      def testCompleteTimerAndIncrementSuccessCounter(): Unit = withMetricsTimer(metricId) { t =>
        t.completeTimerAndIncrementSuccessCounter()

        val inOrder = Mockito.inOrder(metricsOperator, timer)

        inOrder.verify(metricsOperator, times(1)).startTimer(metricId)
        inOrder.verify(timer, times(1)).stop()
        inOrder.verify(metricsOperator, times(1)).incrementSuccessCounter(metricId)
      }

      def testCompleteTimerAndIncrementFailedCounter(): Unit = withMetricsTimer(metricId) { t =>
        t.completeTimerAndIncrementFailedCounter()

        val inOrder = Mockito.inOrder(metricsOperator, timer)

        inOrder.verify(metricsOperator, times(1)).startTimer(metricId)
        inOrder.verify(timer, times(1)).stop()
        inOrder.verify(metricsOperator, times(1)).incrementFailedCounter(metricId)
      }
    }
  }

  "completeTimerAndIncrementSuccessCounter should start/stop the timer and increment the success counter in a specific order" in new SetUp {
    new TestHasMetrics().testCompleteTimerAndIncrementSuccessCounter
  }

  "completeTimerAndIncrementFailedCounter should start/stop the timer and increment the failed counter in a specific order" in new SetUp {
    new TestHasMetrics().testCompleteTimerAndIncrementFailedCounter
  }
}
