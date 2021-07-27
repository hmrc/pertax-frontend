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
import models._
import org.joda.time.LocalDate
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, eq => meq, _}
import play.api.http.Status._
import play.api.libs.json.{JsNull, JsObject, JsString, Json}
import services.http.FakeSimpleHttp
import uk.gov.hmrc.domain.{Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
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
        if (simulateCitizenDetailsServiceIsDown) new FakeSimpleHttp(Right(anException))
        else new FakeSimpleHttp(Left(httpResponse))
      }
      val serviceConfig = app.injector.instanceOf[ServicesConfig]

      val timer = mock[Timer.Context]
      val citizenDetailsService: CitizenDetailsService =
        new CitizenDetailsService(fakeSimpleHttp, mock[Metrics], serviceConfig) {
          override val metricsOperator: MetricsOperator = mock[MetricsOperator]
          when(metricsOperator.startTimer(any())) thenReturn timer
        }

      (citizenDetailsService, citizenDetailsService.metricsOperator, timer)
    }
  }

  "Calling CitizenDetailsService.fakePersonDetails" must {

    trait LocalSetup extends SpecSetup {
      val metricId = "get-person-details"
    }

    "return a PersonDetailsSuccessResponse when called with an existing nino" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(OK, Some(jsonPersonDetails))

      val result = service.personDetails(nino).futureValue

      result mustBe PersonDetailsSuccessResponse(personDetails)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return PersonDetailsNotFoundResponse when called with an unknown nino" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(NOT_FOUND)

      val result = service.personDetails(nino).futureValue

      result mustBe PersonDetailsNotFoundResponse

      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return PersonDetailsHiddenResponse when a locked hidden record (MCI) is asked for" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(LOCKED)

      val result = service.personDetails(nino).futureValue

      result mustBe PersonDetailsHiddenResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return PersonDetailsUnexpectedResponse when an unexpected status is returned" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse //For example

      val result = service.personDetails(nino).futureValue

      result mustBe PersonDetailsUnexpectedResponse(seeOtherResponse)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return PersonDetailsErrorResponse when hod call results in an exception" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = true
      override lazy val httpResponse = ???

      val result = service.personDetails(nino).futureValue

      result mustBe PersonDetailsErrorResponse(anException)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }

  "calling CitizenDetailsService.updateAddress" must {

    trait LocalSetup extends SpecSetup {
      val metricId = "update-address"
    }

    "return UpdateAddressSuccessResponse when called with valid Nino and address data" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(CREATED)

      val result = service.updateAddress(nino, "115", address).futureValue

      result mustBe UpdateAddressSuccessResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return UpdateAddressSuccessResponse when called with a valid Nino and valid correspondence address with an end date" in new LocalSetup {
      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(CREATED)

      val result = service.updateAddress(nino, "115", correspondenceAddress).futureValue

      result mustBe UpdateAddressSuccessResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return UpdateAddressBadRequestResponse when Citizen Details service returns BAD_REQUEST" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(BAD_REQUEST)

      val result = service.updateAddress(nino, "115", address).futureValue

      result mustBe UpdateAddressBadRequestResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return UpdateAddressUnexpectedResponse when an unexpected status is returned" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse //For example

      val result = service.updateAddress(nino, "115", address).futureValue

      result mustBe UpdateAddressUnexpectedResponse(seeOtherResponse)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return UpdateAddressErrorResponse when Citizen Details service returns an exception" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = true
      override lazy val httpResponse = ???

      val result = service.updateAddress(nino, "115", address).futureValue

      result mustBe UpdateAddressErrorResponse(anException)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }

  "Calling CitizenDetailsService.getMatchingDetails" must {

    trait LocalSetup extends SpecSetup {
      val metricId = "get-matching-details"
    }

    "return MatchingDetailsSuccessResponse containing an SAUTR when the service returns an SAUTR" in new LocalSetup {

      val saUtr = new SaUtrGenerator().nextSaUtr.utr
      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(OK, Some(Json.obj("ids" -> Json.obj("sautr" -> saUtr))))

      val result = service.getMatchingDetails(nino).futureValue

      result mustBe MatchingDetailsSuccessResponse(MatchingDetails(Some(SaUtr(saUtr))))
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return MatchingDetailsSuccessResponse containing no SAUTR when the service does not return an SAUTR" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(OK, Some(Json.obj("ids" -> Json.obj("sautr" -> JsNull))))

      val result = service.getMatchingDetails(nino).futureValue

      result mustBe MatchingDetailsSuccessResponse(MatchingDetails(None))
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return MatchingDetailsNotFoundResponse when citizen-details returns an 404" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      override lazy val httpResponse = HttpResponse(NOT_FOUND)

      val result = service.getMatchingDetails(nino).futureValue

      result mustBe MatchingDetailsNotFoundResponse
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return MatchingDetailsUnexpectedResponse when citizen-details returns an unexpected response code" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = false
      val seeOtherResponse = HttpResponse(SEE_OTHER)
      override lazy val httpResponse = seeOtherResponse //For example

      val result = service.getMatchingDetails(nino).futureValue

      result mustBe MatchingDetailsUnexpectedResponse(seeOtherResponse)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return MatchingDetailsErrorResponse when hod call results in another exception" in new LocalSetup {

      override lazy val simulateCitizenDetailsServiceIsDown = true
      override lazy val httpResponse = ???

      val result = service.getMatchingDetails(nino).futureValue

      result mustBe MatchingDetailsErrorResponse(anException)
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }

  }

  "Calling CitizenDetailsService.getEtag" must {

    trait LocalSetup extends SpecSetup {
      val metricId = "get-etag"
    }

    "return an etag when citizen-details returns 200" in new LocalSetup {
      override def httpResponse: HttpResponse =
        HttpResponse(OK, Some(JsObject(Seq(("etag", JsString("115"))))))

      override def simulateCitizenDetailsServiceIsDown: Boolean = false

      val result = service.getEtag(nino.nino).futureValue

      result mustBe Some(ETag("115"))
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    "return None" when {

      "citizen-details returns 404" in new LocalSetup {
        override def httpResponse: HttpResponse = HttpResponse(NOT_FOUND)

        override def simulateCitizenDetailsServiceIsDown: Boolean = false

        val result = service.getEtag(nino.nino).futureValue

        result mustBe None
        verify(met, times(1)).startTimer(metricId)
        verify(met, times(1)).incrementFailedCounter(metricId)
        verify(timer, times(1)).stop()
      }

      "citizen-details returns 423" in new LocalSetup {
        override def httpResponse: HttpResponse = HttpResponse(LOCKED)

        override def simulateCitizenDetailsServiceIsDown: Boolean = false

        val result = service.getEtag(nino.nino).futureValue

        result mustBe None
        verify(met, times(1)).startTimer(metricId)
        verify(met, times(1)).incrementFailedCounter(metricId)
        verify(timer, times(1)).stop()
      }

      "citizen-details returns 500" in new LocalSetup {
        override def httpResponse: HttpResponse = HttpResponse(INTERNAL_SERVER_ERROR)

        override def simulateCitizenDetailsServiceIsDown: Boolean = false

        val result = service.getEtag(nino.nino).futureValue

        result mustBe None
        verify(met, times(1)).startTimer(metricId)
        verify(met, times(1)).incrementFailedCounter(metricId)
        verify(timer, times(1)).stop()
      }

      "the call to citizen-details throws an exception" in new LocalSetup {
        override def httpResponse: HttpResponse = HttpResponse(INTERNAL_SERVER_ERROR)

        override def simulateCitizenDetailsServiceIsDown: Boolean = true

        val result = service.getEtag(nino.nino).futureValue

        result mustBe None
        verify(met, times(1)).startTimer(metricId)
        verify(met, times(1)).incrementFailedCounter(metricId)
        verify(timer, times(1)).stop()
      }
    }
  }
}
