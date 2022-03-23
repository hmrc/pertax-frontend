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
import org.joda.time.DateTime
import org.scalatest.concurrent.IntegrationPatience

class RateLimiterSpec extends BaseSpec with Throttle with WireMockHelper with IntegrationPatience {

  private val system: ActorSystem = ActorSystem()

  def workFuture = Future.successful(true)

  "withThrottle" must {
    "execute future" when {
      "not rate limited" in {
        val result = withThrottle(10, 10 seconds) {
          workFuture
        }

        result.futureValue mustBe true
      }
    }

    "fail to execute future" when {
      "rate limit is reached" in {
        val result = for {
          _ <-
            withThrottle(1, 1 millisecond) {
              workFuture
            }
          result <-
            withThrottle(1, 1 millisecond) {
              workFuture
            }
        } yield result

        whenReady(result.failed) { e =>
          e mustBe RateLimitedException
        }
      }
    }
  }
}
