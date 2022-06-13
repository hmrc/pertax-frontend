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
import uk.gov.hmrc.circuitbreaker.CircuitBreakerConfig
import uk.gov.hmrc.http.{HttpException, TooManyRequestException, UpstreamErrorResponse}
import util.BaseSpec

class ServicesCircuitBreakerSpec extends BaseSpec with ServicesCircuitBreaker {

  val configDecorator = injected[ConfigDecorator]

  override protected val externalServiceName: String = configDecorator.breathingSpaceAppName

  override protected def circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig(
    serviceName = externalServiceName
  )

  "breakOnException" should {
    "return true for HttpException and 5xx response code" in {
      val throwable = new HttpException("message", 500)
      breakOnException(throwable) mustBe true
    }

    "return true for Upstream5xxResponse" in {
      val throwable = UpstreamErrorResponse("message", 500, 500)
      breakOnException(throwable) mustBe true
    }

    "return false for Upstream4xxResponse" in {
      val throwable = UpstreamErrorResponse("message", 400, 400)
      breakOnException(throwable) mustBe false
    }

    "return true for HttpException and 429 response code" in {
      val throwable = new HttpException("TooManyRequest", 429)
      breakOnException(throwable) mustBe true
    }

    "return false for HttpException and 400 response code" in {
      val throwable = new HttpException("TooManyRequest", 400)
      breakOnException(throwable) mustBe false
    }

    "return false for any other Throwable" in {
      val throwable = new Throwable()
      breakOnException(throwable) mustBe false
    }
  }

}
