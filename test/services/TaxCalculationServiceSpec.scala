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
import metrics.MetricsOperator
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.{Configuration, Environment}
import play.api.Mode.Mode
import play.api.http.Status._
import play.api.libs.json.Json
import services.http.FakeSimpleHttp
import uk.gov.hmrc.domain.Nino
import util.{BaseSpec, Fixtures}
import uk.gov.hmrc.http.HttpResponse

class TaxCalculationServiceSpec extends BaseSpec {

  trait SpecSetup {
    def httpResponse: HttpResponse
    def simulateTaxCalculationServiceIsDown: Boolean

    val taxCalcDetails = Fixtures.buildTaxCalculation
    val jsonTaxCalcDetails = Json.toJson(taxCalcDetails)
    val anException = new RuntimeException("Any")
    val metricId = "get-taxcalc-summary"

    lazy val r = service.getTaxCalculation(Fixtures.fakeNino, 2015)

    lazy val (service, metrics, timer) = {
      val fakeSimpleHttp = {
        if(simulateTaxCalculationServiceIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val timer = MockitoSugar.mock[Timer.Context]
      val taxCalculationService: TaxCalculationService = new TaxCalculationService(injected[Environment], injected[Configuration], fakeSimpleHttp, MockitoSugar.mock[Metrics]) {

        override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
      }

      (taxCalculationService, taxCalculationService.metricsOperator, timer)
    }
  }


  "Calling TaxCalculationService.getTaxCalculation" should {

    "return a TaxCalculationSuccessResponse when the http client returns a TaxCalculation object" in new SpecSetup {
      override lazy val httpResponse = HttpResponse(OK, Some(jsonTaxCalcDetails))
      override lazy val simulateTaxCalculationServiceIsDown = false

      await(r) shouldBe TaxCalculationSuccessResponse(taxCalcDetails)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxCalculationNotFoundResponse when the http client returns a NOT FOUND response" in new SpecSetup {
      override lazy val httpResponse = HttpResponse(NOT_FOUND)
      override lazy val simulateTaxCalculationServiceIsDown = false

      await(r) shouldBe TaxCalculationNotFoundResponse
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxCalculationUnexpectedResponse when an unexpected status is returned" in new SpecSetup {
      override lazy val simulateTaxCalculationServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse

      await(r) shouldBe TaxCalculationUnexpectedResponse(seeOtherResponse)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxCalculationErrorResponse when called and service is down" in new SpecSetup {
      override lazy val simulateTaxCalculationServiceIsDown = true
      override lazy val httpResponse = ???

      await(r) shouldBe TaxCalculationErrorResponse(anException)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

  }

}
