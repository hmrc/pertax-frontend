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
import models._
import org.joda.time.LocalDate
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.{Configuration, Environment}
import play.api.Mode.Mode
import play.api.http.Status._
import play.api.libs.json.{JsNull, Json}
import services.http.FakeSimpleHttp
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HttpResponse
import util.{BaseSpec, Fixtures}

class CitizenDetailsServiceSpec extends BaseSpec {

  trait SpecSetup {

    def httpResponse: HttpResponse
    def simulateCitizenDetailsServiceIsDown: Boolean

    val personDetails = Fixtures.buildPersonDetails
    val jsonPersonDetails = Json.toJson(personDetails)
    val address = Address(
      Some("1 Fake Street"),
      Some("Fake Town"),
      Some("Fake City"),
      Some("Fake Region"),
      None,
      None,
      Some("AA1 1AA"),
      Some(new LocalDate(2015, 3, 15)),
      None,
      Some("Residential")
    )

    val correspondenceAddress = Address(
      Some("3 Fake Street"),
      Some("Fake Town"),
      Some("FakeShire"),
      Some("Fake Region"),
      None,
      None,
      Some("AA1 2AA"),
      Some(new LocalDate(2015, 3, 15)),
      Some(LocalDate.now),
      Some("Correspondence")
    )

    val jsonAddress = Json.obj("etag" -> "115", "address" -> Json.toJson(address))
    val jsonCorrespondenceAddress = Json.obj("etag" -> "115", "address" -> Json.toJson(correspondenceAddress))
    val nino: Nino = Fixtures.fakeNino
    val anException = new RuntimeException("Any")

    lazy val (service, met, timer) = {

      val fakeSimpleHttp = {
        if(simulateCitizenDetailsServiceIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }

      val timer = MockitoSugar.mock[Timer.Context]
      val citizenDetailsService: CitizenDetailsService = new CitizenDetailsService(injected[Environment], injected[Configuration], fakeSimpleHttp, MockitoSugar.mock[Metrics]) {
        override val metricsOperator: MetricsOperator = MockitoSugar.mock[MetricsOperator]
        when(metricsOperator.startTimer(any())) thenReturn timer
      }

      (citizenDetailsService, citizenDetailsService.metricsOperator, timer)
    }
  }


  "Calling CitizenDetailsService.personDetails" should {

    trait LocalSetup extends SpecSetup {
      val metricId = "get-person-details"
    }

    "return a PersonDetailsSuccessResponse when called with an existing nino" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(OK, Some(jsonPersonDetails))

      val r = service.personDetails(nino)

      await(r) shouldBe PersonDetailsSuccessResponse(personDetails)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return PersonDetailsNotFoundResponse when called with an unknown nino" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(NOT_FOUND)

      val r = service.personDetails(nino)

      await(r) shouldBe PersonDetailsNotFoundResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return PersonDetailsHiddenResponse when a locked hidden record (MCI) is asked for" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(LOCKED)

      val r = service.personDetails(nino)

      await(r) shouldBe PersonDetailsHiddenResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return PersonDetailsUnexpectedResponse when an unexpected status is returned" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse  //For example

      val r = service.personDetails(nino)

      await(r) shouldBe PersonDetailsUnexpectedResponse(seeOtherResponse)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return PersonDetailsErrorResponse when hod call results in an exception" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = true
      override lazy val httpResponse = ???

      val r = service.personDetails(nino)

      await(r) shouldBe PersonDetailsErrorResponse(anException)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }

  "calling CitizenDetailsService.updateAddress" should {

    trait LocalSetup extends SpecSetup {
      val metricId = "update-address"
    }

    "return UpdateAddressSuccessResponse when called with valid Nino and address data" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(CREATED)

      val r = service.updateAddress(nino, "115", address)

      await(r) shouldBe UpdateAddressSuccessResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return UpdateAddressSuccessResponse when called with a valid Nino and valid correspondence address with an end date" in new LocalSetup {
      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(CREATED)

      val r = service.updateAddress(nino, "115", correspondenceAddress)

      await(r) shouldBe UpdateAddressSuccessResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return UpdateAddressBadRequestResponse when Citizen Details service returns BAD_REQUEST" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(BAD_REQUEST)

      val r = service.updateAddress(nino, "115", address)

      await(r) shouldBe UpdateAddressBadRequestResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return UpdateAddressUnexpectedResponse when an unexpected status is returned" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse  //For example

      val r = service.updateAddress(nino, "115", address)

      await(r) shouldBe UpdateAddressUnexpectedResponse(seeOtherResponse)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return UpdateAddressErrorResponse when Citizen Details service returns an exception" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = true
      override lazy val httpResponse = ???

      val r = service.updateAddress(nino, "115", address)

      await(r) shouldBe UpdateAddressErrorResponse(anException)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }

  "Calling CitizenDetailsService.getMatchingDetails" should {

    trait LocalSetup extends SpecSetup {
      val metricId = "get-matching-details"
    }

    "return MatchingDetailsSuccessResponse containing an SAUTR when the service returns an SAUTR" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(OK, Some(Json.obj("ids" -> Json.obj("sautr" -> "1111111111"))))

      val r = service.getMatchingDetails(nino)

      await(r) shouldBe MatchingDetailsSuccessResponse(MatchingDetails(Some(SaUtr("1111111111"))))
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return MatchingDetailsSuccessResponse containing no SAUTR when the service does not return an SAUTR" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(OK, Some(Json.obj("ids" -> Json.obj("sautr" -> JsNull))))

      val r = service.getMatchingDetails(nino)

      await(r) shouldBe MatchingDetailsSuccessResponse(MatchingDetails(None))
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return MatchingDetailsNotFoundResponse when citizen-details returns an 404" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(NOT_FOUND)

      val r = service.getMatchingDetails(nino)

      await(r) shouldBe MatchingDetailsNotFoundResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return MatchingDetailsUnexpectedResponse when citizen-details returns an unexpected response code" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse //For example

      val r = service.getMatchingDetails(nino)

      await(r) shouldBe MatchingDetailsUnexpectedResponse(seeOtherResponse)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return MatchingDetailsErrorResponse when hod call results in another exception" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = true
      override lazy val httpResponse = ???

      val r = service.getMatchingDetails(nino)

      await(r) shouldBe MatchingDetailsErrorResponse(anException)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

  }

}
