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

import akka.pattern.CircuitBreaker
import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import metrics.MetricsOperator
import metrics.MetricsOperator.Metric
import models.TaxComponents
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito.{when, _}
import org.scalatest.mockito._
import org.scalatestplus.play.OneAppPerSuite
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration, Environment}
import services.http.{FakeSimpleHttp, SimpleHttp}
import uk.gov.hmrc.http.HttpResponse
import util.{BaseSpec, Fixtures}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaiServiceSpec extends BaseSpec with OneAppPerSuite  {

  override lazy val app: Application = {
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.tai.circuit-breaker.max-failures"  -> 1,
        "microservice.services.tai.circuit-breaker.reset-timeout" -> 1
      )
      .build()
  }

  trait SpecSetup {
    def httpResponse: HttpResponse
    def simulateTaiServiceIsDown: Boolean


    val taxComponentsJson = Json.parse("""{
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
    lazy val (metrics, timer) = {

      val timer = MockitoSugar.mock[Timer.Context]

      val metrics = MockitoSugar.mock[MetricsOperator]
      when(metrics.startTimer(any())) thenReturn timer

      (metrics, timer)
    }

   lazy val fakeSimpleHttp = {
      if(simulateTaiServiceIsDown) new FakeSimpleHttp(Right(anException))
      else new FakeSimpleHttp(Left(httpResponse))
    }
    def service(simpleHttp:SimpleHttp=fakeSimpleHttp)= {

      val m = metrics


      new TaiService(
        injected[Environment], injected[Configuration], injected[CircuitBreaker],simpleHttp, MockitoSugar.mock[Metrics]
      ) {
         override val metricsOperator: MetricsOperator = m
     }
    }
  }


  "Calling TaiService.taxSummary" should {

    trait LocalSetup extends SpecSetup {
      val metricId:Metric = "get-tax-components"
    }

    "circuitBreaker must call tai" in new LocalSetup {
      override lazy val simulateTaiServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      val mockSimpleHttp=MockitoSugar.mock[SimpleHttp]
      override lazy val httpResponse = seeOtherResponse
      when(mockSimpleHttp.get[TaxComponentsResponse](Matchers.any())(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(TaxComponentsUnexpectedResponse(seeOtherResponse)),
          Future.successful(TaxComponentsSuccessResponse(TaxComponents(Seq("EmployerProvidedServices", "PersonalPensionPayments")))))

      val s=service(mockSimpleHttp)

      val result1 = s.taxComponents(Fixtures.fakeNino, 2014)
      await(result1) shouldBe TaxComponentsCircuitOpenResponse

      val result2 = s.taxComponents(Fixtures.fakeNino, 2014)
      await(result2) shouldBe TaxComponentsCircuitOpenResponse

      Thread.sleep(2000)

      val result3 = s.taxComponents(Fixtures.fakeNino, 2014)
      await(result3) shouldBe TaxComponentsSuccessResponse(TaxComponents(Seq("EmployerProvidedServices", "PersonalPensionPayments")))

 }

    "return a TaxComponentsSuccessResponse containing a TaxSummaryDetails object when called with an existing nino and year" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      override lazy val httpResponse = HttpResponse(OK, Some(taxComponentsJson))

      val r = service().taxComponents(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxComponentsSuccessResponse(TaxComponents(Seq("EmployerProvidedServices", "PersonalPensionPayments")))
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxComponentsUnexpectedResponse when an unexpected status is returned" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse  //For example

      val r = service().taxComponents(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxComponentsUnexpectedResponse(seeOtherResponse)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxComponentsUnavailableResponse when called with a nino that returns 404" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      override lazy val httpResponse = HttpResponse(NOT_FOUND)

      val r = service().taxComponents(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxComponentsUnavailableResponse
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxComponentsUnavailableResponse when called with a nino that returns 400" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = false
      override lazy val httpResponse = HttpResponse(BAD_REQUEST)

      val r = service().taxComponents(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxComponentsUnavailableResponse
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return TaxComponentsErrorResponse when called and service is down" in new LocalSetup {

      override lazy val simulateTaiServiceIsDown = true
      override lazy val httpResponse = ???

      val r = service().taxComponents(Fixtures.fakeNino, 2014)

      await(r) shouldBe TaxComponentsErrorResponse(anException)
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }
}
