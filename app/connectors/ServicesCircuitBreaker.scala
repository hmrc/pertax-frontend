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

package connectors

import config.ConfigDecorator
import play.api.http.Status.TOO_MANY_REQUESTS
import uk.gov.hmrc.circuitbreaker.{CircuitBreakerConfig, UsingCircuitBreaker}
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.is5xx
import uk.gov.hmrc.http.UpstreamErrorResponse.{Upstream4xxResponse, Upstream5xxResponse}

trait ServicesCircuitBreaker extends UsingCircuitBreaker {

  protected val externalServiceName: String

  val configDecorator: ConfigDecorator

  override protected def circuitBreakerConfig = CircuitBreakerConfig(
    serviceName = externalServiceName,
    numberOfCallsToTriggerStateChange = configDecorator.numberOfCallsToTriggerStateChange(externalServiceName),
    unavailablePeriodDuration = configDecorator.unavailablePeriodDuration(externalServiceName),
    unstablePeriodDuration = configDecorator.unstablePeriodDuration(externalServiceName)
  )

  override def breakOnException(throwable: Throwable): Boolean =
    throwable match {
      case error: UpstreamErrorResponse if error.statusCode == TOO_MANY_REQUESTS || error.statusCode >= 499 => true
      case _                                                                                                => false
    }

}
