/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.common.util.concurrent.RateLimiter
import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}

import scala.concurrent.Future

@Singleton
class Limiters @Inject() (configuration: Configuration) {
  private lazy val maxTpsForGetClientStatus      = configuration
    .getOptional[Double]("feature.agent-client-relationships.maxTps")
    .getOrElse(100.0)
  val rateLimiterForGetClientStatus: RateLimiter = RateLimiter.create(maxTpsForGetClientStatus)
}

case object RateLimitedException extends RuntimeException

trait Throttle extends Logging {
  def rateLimiter: RateLimiter

  def withThrottle[A](
    block: => Future[A]
  ): Future[A] =
    if (rateLimiter.tryAcquire()) {
      block
    } else {
      val exception = new RuntimeException(
        s"Request failed to acquire a permit at a tps of ${rateLimiter.getRate}"
      )
      logger.error(exception.getMessage + "\n" + exception.getStackTrace.mkString("\n"))
      Future.failed(RateLimitedException)
    }
}
