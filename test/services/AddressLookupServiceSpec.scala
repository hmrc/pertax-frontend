/*
 * Copyright 2020 HM Revenue & Customs
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
import config.ConfigDecorator
import models.addresslookup.RecordSet
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.{Configuration, Environment}
import services.http.FakeSimpleHttp
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.Fixtures._
import util.{BaseSpec, Tools}

import scala.concurrent.Future
import scala.io.Source

class AddressLookupServiceSpec extends BaseSpec {

  trait SpecSetup {
    def httpResponse: HttpResponse
    def simulateAddressLookupServiceIsDown: Boolean

    val oneAndTwoOtherPlacePafRecordSetJson: String =
      Source.fromInputStream(getClass.getResourceAsStream("/address-lookup/recordSet.json")).mkString

    val addressesWithMissingLinesRecordSetJson: String =
      Source
        .fromInputStream(getClass.getResourceAsStream("/address-lookup/recordSetWithMissingAddressLines.json"))
        .mkString

    val expectedRecordSet = oneAndTwoOtherPlacePafRecordSet
    val expectedRecordSetJson = Json.parse(oneAndTwoOtherPlacePafRecordSetJson)

    val expectedRecordSetMissingLinesJson: JsValue = Json.parse(addressesWithMissingLinesRecordSetJson)

    val emptyRecordSet = RecordSet(List())
    val emptyRecordSetJson = Json.parse("[]")

    val anException = new RuntimeException("Any")

    lazy val (service, met, timer, client) = {

      val fakeSimpleHttp = {
        if (simulateAddressLookupServiceIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val timer = MockitoSugar.mock[Timer.Context]

      val fakeTools = new Tools(injected[ApplicationCrypto])
      val serviceConfig = app.injector.instanceOf[ServicesConfig]

      val addressLookupService: AddressLookupService = new AddressLookupService(
        injected[Environment],
        injected[Configuration],
        injected[ConfigDecorator],
        fakeSimpleHttp,
        MockitoSugar.mock[Metrics],
        fakeTools,
        serviceConfig) {
        override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
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
      headerCarrier.extraHeaders.contains(("X-Hmrc-Origin", "PERTAX")) shouldBe true
    }

    "return a List of addresses matching the given postcode, if any matching record exists" in new LocalSetup {
      await(r) shouldBe AddressLookupSuccessResponse(expectedRecordSet)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return a List of addresses matching the given postcode and house name/number, if any matching record exists" in new LocalSetup {
      override lazy val r = service.lookup("ZZ11ZZ", Some("2"))

      await(r) shouldBe AddressLookupSuccessResponse(expectedRecordSet)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return a List of addresses filtering addresse out with no lines" in new LocalSetup {
      override lazy val r: Future[AddressLookupResponse] = service.lookup("ZZ11ZZ", Some("2"))
      override lazy val httpResponse = HttpResponse(OK, Some(expectedRecordSetMissingLinesJson))

      await(r) shouldBe AddressLookupSuccessResponse(twoOtherPlaceRecordSet)
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
