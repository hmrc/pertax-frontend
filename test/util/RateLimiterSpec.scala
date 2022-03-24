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

import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, urlEqualTo}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.ActorSystem
import com.google.common.util.concurrent.RateLimiter
import cats.implicits._
import com.github.nscala_time.time.Imports.DateTime
import org.scalatest.concurrent.IntegrationPatience

import scala.util.{Failure, Success}

class RateLimiterSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  def workFuture = Future.successful(true)

  class TestThrottle(tps: Double) extends Throttle {
    private var rateLimiter: Option[RateLimiter] = None
    def getInstance: RateLimiter = rateLimiter match {
      case Some(rateLimiter) =>
        println("some")
        rateLimiter
      case None =>
        println("none")
        rateLimiter = Some(RateLimiter.create(tps))
        rateLimiter.get
    }
  }

  "withThrottle" must {
    "execute future" when {
      "below rate limit" in new TestThrottle(10) {
        def workFuture: Future[Boolean] = Future.successful {
          Thread.sleep(500)
          true
        }

        val waitTime = 10 seconds

        val result = withThrottle(getInstance, waitTime) {
          workFuture
        }

        result.futureValue mustBe true
      }
    }

    "fail to execute future" when {
      "rate limit is reached at 1tps" in new TestThrottle(1) {
        val result = for {
          _ <-
            withThrottle(getInstance, 1 millisecond) {
              workFuture
            }
          result <-
            withThrottle(getInstance, 1 millisecond) {
              workFuture
            }
        } yield result

        whenReady(result.failed) { e =>
          e mustBe RateLimitedException
        }
      }
    }

    "fail to execute future" when {
      "rate limit is reached at 1tps 0 wait" in new TestThrottle(1) {
        val result = for {
          _ <-
            withThrottle(getInstance, 0 millisecond) {
              workFuture
            }
          result <-
            withThrottle(getInstance, 0 millisecond) {
              workFuture
            }
        } yield result

        whenReady(result.failed) { e =>
          e mustBe RateLimitedException
        }
      }

      "rate limit is reached with delayed block" in new TestThrottle(1) {
        def workFuture: Future[Boolean] = Future.successful {
          Thread.sleep(500)
          true
        }
        val result = for {
          resultA <-
            withThrottle(getInstance, 10 millisecond) {
              workFuture
            }
          _ <-
            Future.successful(Thread.sleep(600))
          resultB <-
            withThrottle(getInstance, 10 millisecond) {
              workFuture
            }
        } yield (resultA, resultB)

        result.futureValue._1 mustBe true
        result.futureValue._2 mustBe true
      }

      "long request does not block following" in new TestThrottle(1) {
        def workFuture: Future[Boolean] = Future.successful {
          Thread.sleep(1500)
          true
        }
        val result = for {
          resultA <-
            withThrottle(getInstance, 10 millisecond) {
              workFuture
            }
          resultB <-
            withThrottle(getInstance, 10 millisecond) {
              workFuture
            }
        } yield (resultA, resultB)

        result.futureValue._1 mustBe true
        result.futureValue._2 mustBe true
      }

      "wait time in action" in new TestThrottle(1) {
        def workFuture: Future[Boolean] = Future.successful(true)

        val result = for {
          resultA <-
            withThrottle(getInstance, 10 millisecond) {
              workFuture
            }
          resultB <-
            withThrottle(getInstance, 2000 millisecond) {
              workFuture
            }
        } yield (resultA, resultB)

        result.futureValue._1 mustBe true
        result.futureValue._2 mustBe true
      }

      "three requests over 2 seconds" in new TestThrottle(1) {
        def workFuture: Future[Boolean] = Future.successful {
          println(DateTime.now())
          true
        }
        val resultA =
          withThrottle(getInstance, 10 millisecond) {
            workFuture
          }
        val resultB =
          withThrottle(getInstance, 10 millisecond) {
            workFuture
          }
        val resultC =
          withThrottle(getInstance, 1500 millisecond) {
            workFuture
          }

        List(resultA, resultB).foldLeft(Future.successful(true)) { case (accumulator, currentValue) =>
          currentValue.onComplete {
            case Success(_) => {
              accumulator.onComplete {
                case Success(_) => accumulator
                case Failure(_) => Future.successful(false)
              }
            }
            case Failure(_) => Future.successful(false)
          }
        }

        val finalResult: Future[Boolean] = (resultA, resultB, resultC).mapN((_, _, _) => true)

        finalResult.map(x => println(x))

        whenReady(finalResult.failed) { e =>
          println("1" * 100)
          println(e)
          e mustBe RateLimitedException
        }
      }
    }
  }
}
