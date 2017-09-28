/*
 * Copyright 2017 HM Revenue & Customs
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
import services.http.FakeSimpleHttp
import uk.gov.hmrc.domain.SaUtr
import util.BaseSpec
import uk.gov.hmrc.http.HttpResponse


class EnrolmentExceptionListServiceSpec extends BaseSpec {

  trait LocalSetup {

    def simulateExceptionListServiceIsDown: Boolean

    def httpResponse: HttpResponse

    lazy val saUtr = SaUtr("1111111111")

    val metricId = "get-enrolment-exception"

    lazy val (service, met, timer) = {

      val fakeSimpleHttp = {
        if(simulateExceptionListServiceIsDown) new FakeSimpleHttp(Right(new RuntimeException("Anything")))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val timer = MockitoSugar.mock[Timer.Context]
      val enrolmentExceptionListService: EnrolmentExceptionListService = new EnrolmentExceptionListService(fakeSimpleHttp, MockitoSugar.mock[Metrics]) {
        override val metricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
      }

      (enrolmentExceptionListService, enrolmentExceptionListService.metricsOperator, timer)
    }
  }

  "Calling HasEnrolmentExceptionListService.isAccountIdentityVerificationExempt" should {

    "return true if the SAUTR is present in the enrolment exception list" in new LocalSetup {
      override lazy val simulateExceptionListServiceIsDown = false
      override lazy val httpResponse = HttpResponse(200)

      await(service.isAccountIdentityVerificationExempt(saUtr)) shouldBe true

      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return false if the SAUTR is not present in the enrolment exception list" in new LocalSetup {
      override lazy val simulateExceptionListServiceIsDown = false
      override lazy val httpResponse = HttpResponse(404)

      await(service.isAccountIdentityVerificationExempt(saUtr)) shouldBe false

      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return false if the exception list service returns an unexpected response code" in new LocalSetup {
      override lazy val simulateExceptionListServiceIsDown = false
      override lazy val httpResponse = HttpResponse(503)

      await(service.isAccountIdentityVerificationExempt(saUtr)) shouldBe false

      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return false if the exception list service is down" in new LocalSetup {
      override lazy val simulateExceptionListServiceIsDown = true
      override lazy val httpResponse = ???

      await(service.isAccountIdentityVerificationExempt(saUtr)) shouldBe false

      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }
}
