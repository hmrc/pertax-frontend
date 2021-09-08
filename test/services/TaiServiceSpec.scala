/*
 * Copyright 2021 HM Revenue & Customs
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
import models.TaxComponents
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.any
import play.api.http.Status._
import play.api.libs.json.Json
import services.http.FakeSimpleHttp
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.{BaseSpec, Fixtures}

class TaiServiceSpec extends BaseSpec {

  trait SpecSetup {
    def httpResponse: HttpResponse
    def simulateTaiServiceIsDown: Boolean

    val taxComponentsJson =
      Json.parse("""{
                   |   "data" : [ {
                   |      "componentType" : "EmployerProvidedServices",
                   |      "employmentId" : 12,
                   |      "amount" : 12321,
                   |      "description" : "Some Description",
                   |      "iabdCategory" : "Benefit"
                   |   }, {
                   |      "componentType" : "PersonalPensionPayments",
                   |      "employmentId" : 31,
                   |      "amount" : 12345,
                   |      "description" : "Some Description Some",
                   |      "iabdCategory" : "Allowance"
                   |   } ],
                   |   "links" : [ ]
                   |}""".stripMargin)

    val anException = new RuntimeException("Any")

    lazy val (service, metrics, timer) = {

      val fakeSimpleHttp = {
        if (simulateTaiServiceIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val timer = mock[Timer.Context]
      val serviceConfig = app.injector.instanceOf[ServicesConfig]
      lazy val taiService: TaiService =
        new TaiService(fakeSimpleHttp, mock[Metrics], serviceConfig) {

          override val metricsOperator: MetricsOperator = mock[MetricsOperator]
          when(metricsOperator.startTimer(any())) thenReturn timer
        }

      (taiService, taiService.metricsOperator, timer)
    }
  }

  "Calling TaiService.taxSummary" must {

    trait LocalSetup extends SpecSetup {
      val metricId = "get-tax-components"
    }

    "return a TaxComponentsSuccessResponse containing a TaxSummaryDetails object when called with an existing nino and year" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      override lazy val httpResponse = HttpResponse(OK, Some(taxComponentsJson))

      val result = service.taxComponents(Fixtures.fakeNino, 2014).futureValue

      result mustBe TaxComponentsSuccessResponse(
        TaxComponents(
          Seq("EmployerProvidedServices", "PersonalPensionPayments")
        )
      )
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxComponentsUnexpectedResponse when an unexpected status is returned" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse //For example

      val result = service.taxComponents(Fixtures.fakeNino, 2014).futureValue

      result mustBe TaxComponentsUnexpectedResponse(seeOtherResponse)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxComponentsUnavailableResponse when called with a nino that returns 404" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      override lazy val httpResponse = HttpResponse(NOT_FOUND)

      val result = service.taxComponents(Fixtures.fakeNino, 2014).futureValue

      result mustBe TaxComponentsUnavailableResponse
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxComponentsUnavailableResponse when called with a nino that returns 400" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      override lazy val httpResponse = HttpResponse(BAD_REQUEST)

      val result = service.taxComponents(Fixtures.fakeNino, 2014).futureValue

      result mustBe TaxComponentsUnavailableResponse
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxComponentsErrorResponse when called and service is down" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = true
      override lazy val httpResponse = ???

      val result = service.taxComponents(Fixtures.fakeNino, 2014).futureValue

      result mustBe TaxComponentsErrorResponse(anException)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }
}
