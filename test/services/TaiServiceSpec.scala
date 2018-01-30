/*
 * Copyright 2018 HM Revenue & Customs
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
import models.TaxSummary
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json
import services.http.FakeSimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http._
import util.{BaseSpec, Fixtures}
import uk.gov.hmrc.http.HttpResponse

class TaiServiceSpec extends BaseSpec {

  trait SpecSetup {
    def httpResponse: HttpResponse
    def simulateTaiServiceIsDown: Boolean

    val taxSummaryDetails = Fixtures.exampleTaxSummaryDetailsJson
    val jsonTaxSummaryDetails = Json.parse(taxSummaryDetails)
    val anException = new RuntimeException("Any")

    lazy val (service, metrics, timer) = {

      val fakeSimpleHttp = {
        if(simulateTaiServiceIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val timer = MockitoSugar.mock[Timer.Context]

      lazy val taiService: TaiService = new TaiService(fakeSimpleHttp, MockitoSugar.mock[Metrics]) {

        override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
      }

      (taiService, taiService.metricsOperator, timer)
    }
  }

  "Calling TaiService.taxSummary" should {

    trait LocalSetup extends SpecSetup {
      val metricId = "get-tax-summary"
    }

    "return a TaxSummarySuccessResponse containing a TaxSummaryDetails object when called with an existing nino and year" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      override lazy val httpResponse = HttpResponse(OK, Some(jsonTaxSummaryDetails))

      val r = service.taxSummary(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxSummarySuccessResponse(TaxSummary(Seq("500T"), Seq()))
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxSummaryUnexpectedResponse when an unexpected status is returned" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse  //For example

      val r = service.taxSummary(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxSummaryUnexpectedResponse(seeOtherResponse)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxSummaryUnavailiableResponse when called with a nino that returns 404" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      override lazy val httpResponse = HttpResponse(NOT_FOUND)

      val r = service.taxSummary(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxSummaryUnavailiableResponse
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxSummaryUnavailiableResponse when called with a nino that returns 400" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      override lazy val httpResponse = HttpResponse(BAD_REQUEST)

      val r = service.taxSummary(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxSummaryUnavailiableResponse
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxSummaryErrorResponse when called and service is down" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = true
      override lazy val httpResponse = ???

      val r = service.taxSummary(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxSummaryErrorResponse(anException)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }
}
