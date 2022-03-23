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

package util

import com.google.common.util.concurrent.RateLimiter
import play.api.Logging
import scala.concurrent.duration._
import scala.compat.java8.DurationConverters._
import scala.concurrent.Future

object Limiters {
  private var rateLimiter: Option[RateLimiter] = None
  def getInstance(tps: Double): RateLimiter = rateLimiter match {
    case Some(rateLimiter) =>
      rateLimiter
    case None =>
      rateLimiter = Some(RateLimiter.create(tps))
      rateLimiter.get
  }
}

case object RateLimitedException extends RuntimeException

trait Throttle extends Logging {
  def withThrottle[A](tps: Double, wait: FiniteDuration)(
    block: => Future[A]
  ): Future[A] = {
    val rateLimiter = Limiters.getInstance(tps)
    if (rateLimiter.tryAcquire(wait.toJava)) {
      block
    } else {
      val exception = new RuntimeException(
        s"Request failed to acquire a permit at a tps of $tps and a waiting time of ${wait.toSeconds}s"
      )
      logger.error(exception.getMessage + "\n" + exception.getStackTrace.mkString("\n"))
      Future.failed(RateLimitedException)
    }
  }
}
