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
import org.scalatest.concurrent.IntegrationPatience

class TimeoutSpec extends BaseSpec with Timeout with WireMockHelper with IntegrationPatience {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.agent-client-authorisation.port" -> server.port()
    )
    .build()

  private val system: ActorSystem = ActorSystem()

  val url = "/agent-client-authorisation/status"

  "Timeout" must {
    "not time out within timeout window" in {}

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
