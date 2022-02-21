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

package services

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.http.Status._
import play.api.libs.json.Json
import services.http.FakeSimpleHttp
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.BaseSpec

class IdentityVerificationFrontendServiceSpec extends BaseSpec {

  trait SpecSetup {
    def httpResponse: HttpResponse
    def simulateIdentityVerificationFrontendIsDown: Boolean

    val anException = new RuntimeException("Any")
    val metricId = "get-iv-journey-status"

    lazy val (service, metrics, timer) = {
      val fakeSimpleHttp = {
        if (simulateIdentityVerificationFrontendIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val serviceConfig = app.injector.instanceOf[ServicesConfig]
      val timer = mock[Timer.Context]
      val identityVerificationFrontendService: IdentityVerificationFrontendService =
        new IdentityVerificationFrontendService(fakeSimpleHttp, mock[Metrics], serviceConfig) {

          override val metricsOperator: MetricsOperator = mock[MetricsOperator]
          when(metricsOperator.startTimer(any())) thenReturn timer
        }

      (identityVerificationFrontendService, identityVerificationFrontendService.metricsOperator, timer)
    }
  }

  "Calling IdentityVerificationFrontend.getIVJourneyStatus" must {

    "return an IdentityVerificationSuccessResponse containing a journey status object when called with a journeyId" in new SpecSetup {
      override lazy val httpResponse = HttpResponse(OK, Some(Json.obj("token" -> "1234", "result" -> "LockedOut")))
      override lazy val simulateIdentityVerificationFrontendIsDown = false

      val result = service.getIVJourneyStatus("1234").futureValue

      result mustBe IdentityVerificationSuccessResponse("LockedOut")
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return IdentityVerificationNotFoundResponse when called with a journeyId that causes a NOT FOUND response" in new SpecSetup {
      override lazy val httpResponse = HttpResponse(NOT_FOUND)
      override lazy val simulateIdentityVerificationFrontendIsDown = false

      val result = service.getIVJourneyStatus("4321").futureValue

      result mustBe IdentityVerificationNotFoundResponse
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxCalculationUnexpectedResponse when an unexpected status is returned" in new SpecSetup {
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse
      override lazy val simulateIdentityVerificationFrontendIsDown = false

      val result = service.getIVJourneyStatus("1234").futureValue

      result mustBe IdentityVerificationUnexpectedResponse(seeOtherResponse)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return IdentityVerificationErrorResponse when called and service is down" in new SpecSetup {
      override lazy val httpResponse = ???
      override lazy val simulateIdentityVerificationFrontendIsDown = true

      val result = service.getIVJourneyStatus("1234").futureValue

      result mustBe IdentityVerificationErrorResponse(anException)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

  }
}
