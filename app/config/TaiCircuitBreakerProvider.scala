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

import scala.concurrent.duration._

@Singleton
class TaiCircuitBreakerProvider @Inject()(config: Configuration)
                                         (implicit ec: ExecutionContext, sys: ActorSystem) extends Provider[CircuitBreaker] {

  private val maxFailures = config.getInt("microservice.services.tai.circuit-breaker.max-failures")
    .getOrElse(throw new IllegalStateException("tai.circuit-breaker.max-failures config value not set"))
  private val callTimeout = config.getMilliseconds("microservice.services.tai.circuit-breaker.call-timeout")
    .getOrElse(throw new IllegalStateException("tai.circuit-breaker.call-timeout config value not set")).milliseconds
  private val resetTimeout = config.getMilliseconds("microservice.services.tai.circuit-breaker.reset-timeout")
    .getOrElse(throw new IllegalStateException("tai.circuit-breaker.reset-timeout config value not set")).milliseconds

  override def get(): CircuitBreaker =
    new CircuitBreaker(
      scheduler = sys.scheduler,
      maxFailures = maxFailures,
      callTimeout = callTimeout,
      resetTimeout = resetTimeout
    )
}
