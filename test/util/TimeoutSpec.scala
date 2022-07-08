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

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.ActorSystem
import org.scalatest.concurrent.IntegrationPatience
import testUtils.{BaseSpec, WireMockHelper}

class TimeoutSpec extends BaseSpec with Timeout with WireMockHelper with IntegrationPatience {

  private val system: ActorSystem = ActorSystem()

  "Timeout" must {
    "not time out within timeout window" in {
      val timeoutInSeconds = 2

      val result = withTimeout(timeoutInSeconds.seconds) {
        akka.pattern.after((timeoutInSeconds - 1).seconds, system.scheduler) {
          Future.successful(true)
        }
      }

      result.futureValue mustBe true
    }

    "time out the request after timeout window" in {

      val timeoutInSeconds = 1

      val result = withTimeout(timeoutInSeconds.seconds) {
        akka.pattern.after((timeoutInSeconds + 1).seconds, system.scheduler) {
          Future.successful(true)
        }
      }

      whenReady(result.failed) { e =>
        e mustBe FutureEarlyTimeout
      }
    }
  }
}
