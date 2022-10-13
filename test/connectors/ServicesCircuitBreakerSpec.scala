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
import play.api.http.Status
import uk.gov.hmrc.circuitbreaker.CircuitBreakerConfig
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}
import testUtils.BaseSpec

class ServicesCircuitBreakerSpec extends BaseSpec with ServicesCircuitBreaker with Status {

  val CLIENT_CLOSED_REQUEST: Int       = 499
  val configDecorator: ConfigDecorator = injected[ConfigDecorator]

  override protected val externalServiceName: String = configDecorator.breathingSpaceAppName

  override protected def circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig(
    serviceName = externalServiceName
  )

  "breakOnException" should {
    "return true for HttpException and 5xx response code" in {
      val throwable = new HttpException("message", INTERNAL_SERVER_ERROR)
      breakOnException(throwable) mustBe false
    }

    "return true for Upstream5xxResponse" in {
      val throwable = UpstreamErrorResponse("message", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)
      breakOnException(throwable) mustBe true
    }

    "return true for 429 Upstream4xxResponse" in {
      val throwable = UpstreamErrorResponse("message", TOO_MANY_REQUESTS, TOO_MANY_REQUESTS)
      breakOnException(throwable) mustBe true
    }

    "return true for 499 Upstream4xxResponse" in {
      val throwable = UpstreamErrorResponse("message", CLIENT_CLOSED_REQUEST, CLIENT_CLOSED_REQUEST)
      breakOnException(throwable) mustBe true
    }

    "return false for Upstream4xxResponse" in {
      val throwable = UpstreamErrorResponse("message", BAD_REQUEST, BAD_REQUEST)
      breakOnException(throwable) mustBe false
    }

    "return true for HttpException and 429 response code" in {
      val throwable = new HttpException("TooManyRequest", TOO_MANY_REQUESTS)
      breakOnException(throwable) mustBe false
    }
    "return true for HttpException and 499 response code" in {
      val throwable = new HttpException("unknown exception", CLIENT_CLOSED_REQUEST)
      breakOnException(throwable) mustBe false
    }

    "return false for HttpException and 400 response code" in {
      val throwable = new HttpException("BadRequest", BAD_REQUEST)
      breakOnException(throwable) mustBe false
    }

    "return false for any other Throwable" in {
      val throwable = new Throwable()
      breakOnException(throwable) mustBe false
    }
  }

}
