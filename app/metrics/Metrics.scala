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
import com.codahale.metrics.Timer.Context
import com.google.inject.ImplementedBy
import metrics.MetricsEnumeration.MetricsEnumeration

import javax.inject.Inject

@ImplementedBy(classOf[MetricsImpl])
trait Metrics {

  def startTimer(api: MetricsEnumeration): Timer.Context

  def incrementSuccessCounter(api: MetricsEnumeration): Unit

  def incrementFailedCounter(api: MetricsEnumeration): Unit

}

class MetricsImpl @Inject() (metrics: com.kenshoo.play.metrics.Metrics) extends Metrics {

  val timers = Map(
    MetricsEnumeration.GET_AGENT_CLIENT_STATUS -> metrics.defaultRegistry
      .timer("get-agent-client-status-timer"),
    MetricsEnumeration.GET_SEISS_CLAIMS -> metrics.defaultRegistry
      .timer("get-seiss-claims-timer"),
    MetricsEnumeration.GET_UNREAD_MESSAGE_COUNT -> metrics.defaultRegistry
      .timer("get-unread-message-count-timer"),
    MetricsEnumeration.LOAD_PARTIAL -> metrics.defaultRegistry
      .timer("load-partial-timer"),
    MetricsEnumeration.GET_BREATHING_SPACE_INDICATOR -> metrics.defaultRegistry
      .timer("get-breathing-space-indicator-timer")
  )

  val successCounters = Map(
    MetricsEnumeration.GET_AGENT_CLIENT_STATUS -> metrics.defaultRegistry
      .counter("get-agent-client-status-success-counter"),
    MetricsEnumeration.GET_SEISS_CLAIMS -> metrics.defaultRegistry
      .counter("get-seiss-claims-success-counter"),
    MetricsEnumeration.GET_UNREAD_MESSAGE_COUNT -> metrics.defaultRegistry
      .counter("get-unread-message-count-success-counter"),
    MetricsEnumeration.LOAD_PARTIAL -> metrics.defaultRegistry
      .counter("load-partial-success-counter"),
    MetricsEnumeration.GET_BREATHING_SPACE_INDICATOR -> metrics.defaultRegistry
      .counter("get-breathing-space-indicator-success-counter")
  )

  val failedCounters = Map(
    MetricsEnumeration.GET_AGENT_CLIENT_STATUS -> metrics.defaultRegistry
      .counter("get-agent-client-status-failed-counter"),
    MetricsEnumeration.GET_SEISS_CLAIMS -> metrics.defaultRegistry
      .counter("get-seiss-claims-failed-counter"),
    MetricsEnumeration.GET_UNREAD_MESSAGE_COUNT -> metrics.defaultRegistry
      .counter("get-unread-message-count-failed-counter"),
    MetricsEnumeration.LOAD_PARTIAL -> metrics.defaultRegistry
      .counter("load-partial-failed-counter"),
    MetricsEnumeration.GET_BREATHING_SPACE_INDICATOR -> metrics.defaultRegistry
      .counter("get-breathing-space-indicator-failed-counter")
  )

  override def startTimer(api: MetricsEnumeration): Context = timers(api).time()

  override def incrementSuccessCounter(api: MetricsEnumeration): Unit =
    successCounters(api).inc()

  override def incrementFailedCounter(api: MetricsEnumeration): Unit =
    failedCounters(api).inc()
}
