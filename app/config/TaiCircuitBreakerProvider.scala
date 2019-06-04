/*
 * Copyright 2019 HM Revenue & Customs
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

package config

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import com.google.inject.{Inject, Provider, Singleton}
import play.api.Configuration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

@Singleton
class TaiCircuitBreakerProvider @Inject()(config: Configuration)
                                         (implicit ec: ExecutionContext, sys: ActorSystem) extends Provider[CircuitBreaker] {

  private val maxFailures = config.getInt("microservice.services.tai.circuit-breaker.max-failures").get
  private val callTimeout = FiniteDuration(config.getInt("microservice.services.tai.circuit-breaker.call-timeout").get, "seconds")
  private val resetTimeout = FiniteDuration(config.getInt("microservice.services.tai.circuit-breaker.reset-timeout").get, "minutes")

  override def get(): CircuitBreaker =
    new CircuitBreaker(
      scheduler = sys.scheduler,
      maxFailures = maxFailures,
      callTimeout = callTimeout,
      resetTimeout = resetTimeout
    )
}
