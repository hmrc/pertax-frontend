/*
 * Copyright 2019 HM Revenue & Customs
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

package services

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.{Configuration, Environment}
import services.http.FakeSimpleHttp
import uk.gov.hmrc.http.HttpResponse
import util.{BaseSpec, Fixtures}

class LifetimeAllowanceServiceSpec extends BaseSpec {

  trait SpecSetup {
    def httpResponse: HttpResponse
    def simulateLtaServiceIsDown: Boolean

    val anException = new RuntimeException("Any")
    val metricId = "has-lta-response"

    lazy val (service, metrics, timer) = {
      val fakeSimpleHttp = {
        if (simulateLtaServiceIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val timer = MockitoSugar.mock[Timer.Context]

      val ltaService: LifetimeAllowanceService = new LifetimeAllowanceService(
        injected[Environment],
        injected[Configuration],
        fakeSimpleHttp,
        MockitoSugar.mock[Metrics]) {

        override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
      }

      (ltaService, ltaService.metricsOperator, timer)
    }
  }

  "Calling HasLtaService.getCount" should {

    "return an Option containing the value 1 when called with a nino which has a LTA Protection" in new SpecSetup {
      override lazy val httpResponse = HttpResponse(OK, Some(Json.obj("count" -> 1)))
      override lazy val simulateLtaServiceIsDown = false

      lazy val r = service.getCount(Fixtures.fakeNino)

      await(r) should contain(1)

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return None when the LTA service returns an exception (service is down)" in new SpecSetup {
      override lazy val httpResponse = ???
      override lazy val simulateLtaServiceIsDown = true

      lazy val r = service.getCount(Fixtures.fakeNino)

      await(r) shouldBe None

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

  }

  "Calling HasLtaService.hasLtaProtection" should {

    "return true when the lta service does not return 0 or None" in new SpecSetup {
      override lazy val httpResponse = HttpResponse(OK, Some(Json.obj("count" -> 1)))
      override lazy val simulateLtaServiceIsDown = false

      lazy val r = service.hasLtaProtection(Fixtures.fakeNino)

      await(r) shouldBe true

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return false when the lta service returns 0" in new SpecSetup {
      override lazy val httpResponse = HttpResponse(OK, Some(Json.obj("count" -> 0)))
      override lazy val simulateLtaServiceIsDown = false

      lazy val r = service.hasLtaProtection(Fixtures.fakeNino)

      await(r) shouldBe false

      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return false when the lta service returns None" in new SpecSetup {
      override lazy val httpResponse = ???
      override lazy val simulateLtaServiceIsDown = true

      lazy val r = service.hasLtaProtection(Fixtures.fakeNino)

      await(r) shouldBe false
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }
}
