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

import com.codahale.metrics._
import com.kenshoo.play.metrics.{Metrics => KenshooMetrics}

@deprecated("Use model.Metrics instead")
trait HasMetrics {

  type Metric = String

  val metricsOperator = new MetricsOperator

  def metrics: KenshooMetrics

  lazy val registry = metrics.defaultRegistry

  class MetricsTimer(metric: Metric) {

    val timer = metricsOperator.startTimer(metric)

    def completeTimerAndIncrementSuccessCounter(): Unit = {
      timer.stop()
      metricsOperator.incrementSuccessCounter(metric)
    }

    def completeTimerAndIncrementFailedCounter(): Unit = {
      timer.stop()
      metricsOperator.incrementFailedCounter(metric)
    }
  }

  def withMetricsTimer[T](metric: Metric)(block: MetricsTimer => T): T =
    block(new MetricsTimer(metric))

  class MetricsOperator {

    def startTimer(metric: Metric) = registry.timer(s"$metric-timer").time()
    def stopTimer(context: Timer.Context) = context.stop()
    def incrementSuccessCounter(metric: Metric): Unit = registry.counter(s"$metric-success-counter").inc()
    def incrementFailedCounter(metric: Metric): Unit = registry.counter(s"$metric-failed-counter").inc()
  }

}
