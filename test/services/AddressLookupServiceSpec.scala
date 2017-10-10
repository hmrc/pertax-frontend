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
import controllers.auth.PertaxAuthenticationProvider
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json
import services.http.FakeSimpleHttp
import models.addresslookup.RecordSet
import util.Fixtures._
import util.{BaseSpec, Fixtures}
import uk.gov.hmrc.http.HttpResponse

class AddressLookupServiceSpec extends BaseSpec {

  trait SpecSetup {
    def httpResponse: HttpResponse
    def simulateAddressLookupServiceIsDown: Boolean

    val expectedRecordSet = oneAndTwoOtherPlacePafRecordSet
    val expectedRecordSetJson = Json.parse(oneAndTwoOtherPlacePafRecordSetJson)
    val emptyRecordSet = RecordSet(List())
    val emptyRecordSetJson = Json.parse("[]")

    val anException = new RuntimeException("Any")

    lazy val (service, met, timer, client) = {

      val fakeSimpleHttp = {
        if(simulateAddressLookupServiceIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val timer = MockitoSugar.mock[Timer.Context]

      val addressLookupService: AddressLookupService = new AddressLookupService(fakeSimpleHttp, MockitoSugar.mock[Metrics], MockitoSugar.mock[PertaxAuthenticationProvider]) {
        override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
        when(pertaxAuthenticationProvider.defaultOrigin) thenReturn "PERTAX"
      }

      (addressLookupService, addressLookupService.metricsOperator, timer, fakeSimpleHttp)
    }

    def headerCarrier = client.getLastHeaderCarrier
  }

  "Calling AddressLookupService.lookup" should {

    trait LocalSetup extends SpecSetup {
      val metricId = "address-lookup"
      lazy val simulateAddressLookupServiceIsDown = false
      lazy val r = service.lookup("ZZ11ZZ")
      lazy val httpResponse = HttpResponse(OK, Some(expectedRecordSetJson))
    }

    "contain valid X-Hmrc-Origin in extra headers when lookup service is called" in new LocalSetup {
      await(r)
      headerCarrier.extraHeaders.contains(("X-Hmrc-Origin","PERTAX")) shouldBe true
    }

    "return a List of addresses matching the given postcode, if any matching record exists" in new LocalSetup {
      await(r) shouldBe AddressLookupSuccessResponse(expectedRecordSet)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return a List of addresses matching the given postcode and house name/number, if any matching record exists" in new LocalSetup {
      override lazy val r = service.lookup("ZZ11ZZ",Some("2"))

      await(r) shouldBe AddressLookupSuccessResponse(expectedRecordSet)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return an empty response for the given house name/number and postcode, if matching record doesn't exist" in new LocalSetup {
      override lazy val httpResponse = HttpResponse(OK, Some(emptyRecordSetJson))

      await(r) shouldBe AddressLookupSuccessResponse(emptyRecordSet)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return AddressLookupUnexpectedResponse response, when called and service returns not found" in new LocalSetup {
      override lazy val httpResponse = HttpResponse(NOT_FOUND)

      await(r) shouldBe AddressLookupUnexpectedResponse(httpResponse)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return AddressLookupErrorResponse when called and service is down" in new LocalSetup {
      override lazy val simulateAddressLookupServiceIsDown = true
      override lazy val httpResponse = ???

      await(r) shouldBe AddressLookupErrorResponse(anException)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }
}
