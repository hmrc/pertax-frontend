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
import org.scalatest.concurrent.IntegrationPatience

import scala.concurrent.Future
import scala.concurrent.duration._

class RateLimiterSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  object TestLimiters {
    val limiter1 = RateLimiter.create(1)
    val limiter4 = RateLimiter.create(4)
  }

  def workFuture: Future[Boolean] = Future.successful(true)

  case class FuturesResults(ok: Int, bad: Int, unknwon: Int)

  def gatherFutures(listFutures: List[Future[Boolean]]): Future[FuturesResults] =
    listFutures.foldLeft(Future.successful(FuturesResults(0, 0, 0))) { case (accumulator, currentValue) =>
      currentValue.flatMap { result =>
        if (result)
          accumulator.map(results => results.copy(ok = results.ok + 1))
        else
          accumulator.map(results => results.copy(bad = results.bad + 1))
      } recoverWith {
        case RateLimitedException => accumulator.map(results => results.copy(bad = results.bad + 1))
        case _                    => accumulator.map(results => results.copy(unknwon = results.unknwon + 1))
      }
    }

  "withThrottle" must {
    "execute a single future successfully" in new Throttle {
      val rateLimiter = RateLimiter.create(1)

      def workFuture: Future[Boolean] = Future.successful {
        true
      }

      val waitTime = 10 seconds

      val result = withThrottle {
        workFuture
      }

      result.futureValue mustBe true
    }

    "Execute a future successfully and fails one" in new Throttle {
      val rateLimiter = RateLimiter.create(1)

      val resultA = withThrottle {
        workFuture
      }
      val resultB = withThrottle {
        workFuture
      }

      val counts = gatherFutures(List(resultA, resultB))

      counts.futureValue mustBe FuturesResults(1, 1, 0)
    }

    "2 futures 1.2 seconds executed successfully at 1 tps" in new Throttle {
      val rateLimiter = RateLimiter.create(1)

      val resultA = withThrottle {
        workFuture
      }
      Thread.sleep(1200)
      val resultB = withThrottle {
        workFuture
      }

      val counts = gatherFutures(List(resultA, resultB))

      counts.futureValue mustBe FuturesResults(2, 0, 0)
    }

    "long futures does not block following bucket" in new Throttle {
      val rateLimiter = RateLimiter.create(1)

      def workFuture: Future[Boolean] = Future.successful {
        Thread.sleep(1500)
        true
      }

      val result = for {
        resultA <-
          withThrottle {
            workFuture
          }
        resultB <-
          withThrottle {
            workFuture
          }
      } yield (resultA, resultB)

      result.futureValue._1 mustBe true
      result.futureValue._2 mustBe true
    }
  }
}
