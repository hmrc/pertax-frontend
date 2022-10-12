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

package connectors

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import models._
import java.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.libs.json.{JsNull, JsObject, JsString, Json}
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import services.http.SimpleHttp
import testUtils.{Fixtures, WireMockHelper}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.GatewayTimeoutException
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.util.Random

class CitizenDetailsConnectorSpec extends ConnectorSpec with WireMockHelper with MockitoSugar with DefaultAwaitTimeout {

  override implicit lazy val app: Application = app(
    Map("microservice.services.citizen-details.port" -> server.port())
  )

  val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

  trait SpecSetup {

    def url: String

    val personDetails: PersonDetails = Fixtures.buildPersonDetails

    val address: Address = Address(
      line1 = Some("1 Fake Street"),
      line2 = Some("Fake Town"),
      line3 = Some("Fake City"),
      line4 = Some("Fake Region"),
      line5 = None,
      postcode = None,
      country = Some("AA1 1AA"),
      startDate = Some(LocalDate.of(2015, 3, 15)),
      endDate = None,
      `type` = Some("Residential"),
      isRls = false
    )

    lazy val (service, met, timer) = {

      val fakeSimpleHttp = app.injector.instanceOf[SimpleHttp]
      val serviceConfig  = app.injector.instanceOf[ServicesConfig]
      val timer          = mock[Timer.Context]

      val citizenDetailsConnector: CitizenDetailsConnector =
        new CitizenDetailsConnector(fakeSimpleHttp, mock[Metrics], serviceConfig) {
          override val metricsOperator: MetricsOperator = mock[MetricsOperator]
          when(metricsOperator.startTimer(any())) thenReturn timer
        }

      (citizenDetailsConnector, citizenDetailsConnector.metricsOperator, timer)
    }

    def verifyMetricsSuccess(metricId: String): Any = {
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    def verifyMetricsFailure(metricId: String): Any = {
      verify(met, times(1)).startTimer(metricId)
      verify(met, times(1)).incrementFailedCounter(metricId)
      verify(timer, times(1)).stop()
    }
  }

  "Calling CitizenDetailsService.fakePersonDetails" must {

    trait LocalSetup extends SpecSetup {
      val metricId    = "get-person-details"
      def url: String = s"/citizen-details/$nino/designatory-details"
    }

    "return a PersonDetailsSuccessResponse when called with an existing nino" in new LocalSetup {
      stubGet(url, OK, Some(Json.toJson(personDetails).toString()))

      val result: PersonDetailsResponse = service.personDetails(nino).futureValue
      result mustBe PersonDetailsSuccessResponse(personDetails)
      verifyMetricsSuccess(metricId)
    }

    "return PersonDetailsNotFoundResponse when called with an unknown nino" in new LocalSetup {
      stubGet(url, NOT_FOUND, None)

      val result: PersonDetailsResponse = service.personDetails(nino).futureValue
      result mustBe PersonDetailsNotFoundResponse
      verifyMetricsFailure(metricId)
    }

    "return PersonDetailsHiddenResponse when a locked hidden record (MCI) is asked for" in new LocalSetup {
      stubGet(url, LOCKED, None)

      val result: PersonDetailsResponse = service.personDetails(nino).futureValue
      result mustBe PersonDetailsHiddenResponse
      verifyMetricsFailure(metricId)
    }

    "return PersonDetailsUnexpectedResponse when an unexpected status is returned" in new LocalSetup {
      stubGet(url, IM_A_TEAPOT, None)

      val result: PersonDetailsResponse = service.personDetails(nino).futureValue
      result mustBe a[PersonDetailsUnexpectedResponse]
      result.toString mustBe "PersonDetailsUnexpectedResponse(HttpResponse status=418)"
      verifyMetricsFailure(metricId)
    }

    "return PersonDetailsErrorResponse when hod call results in an exception" in new LocalSetup {
      val delay: Int = 5000
      stubWithDelay(url, OK, None, None, delay)

      val result: PersonDetailsResponse = service.personDetails(nino).futureValue
      result mustBe PersonDetailsErrorResponse(_: GatewayTimeoutException)
      verifyMetricsFailure(metricId)
    }
  }

  "calling CitizenDetailsService.updateAddress" must {

    trait LocalSetup extends SpecSetup {
      val metricId            = "update-address"
      def url: String         = s"/citizen-details/$nino/designatory-details/address"
      val etag: String        = "115"
      val requestBody: String = Json.obj("etag" -> etag, "address" -> Json.toJson(address)).toString()
    }

    "return UpdateAddressSuccessResponse when called with valid Nino and address data" in new LocalSetup {
      stubPost(url, CREATED, Some(requestBody), None)

      val result: UpdateAddressResponse = service.updateAddress(nino, etag, address).futureValue
      result mustBe UpdateAddressSuccessResponse
      verifyMetricsSuccess(metricId)
    }

    "return UpdateAddressSuccessResponse when called with a valid Nino and valid correspondence address with an end date" in new LocalSetup {
      val correspondenceAddress: Address = Address(
        line1 = Some("3 Fake Street"),
        line2 = Some("Fake Town"),
        line3 = Some("FakeShire"),
        line4 = Some("Fake Region"),
        line5 = None,
        postcode = None,
        country = Some("AA1 2AA"),
        startDate = Some(LocalDate.parse("2015-03-15")),
        endDate = Some(LocalDate.now),
        `type` = Some("Correspondence"),
        isRls = false
      )

      override val requestBody: String = Json
        .obj(
          "etag"    -> etag,
          "address" -> Json.toJson(correspondenceAddress)
        )
        .toString()

      stubPost(url, CREATED, Some(requestBody), None)

      val result: UpdateAddressResponse = service.updateAddress(nino, etag, correspondenceAddress).futureValue
      result mustBe UpdateAddressSuccessResponse
      verifyMetricsSuccess(metricId)
    }

    "return UpdateAddressBadRequestResponse when Citizen Details service returns BAD_REQUEST" in new LocalSetup {
      stubPost(url, BAD_REQUEST, Some(requestBody), None)

      val result: UpdateAddressResponse = service.updateAddress(nino, etag, address).futureValue
      result mustBe UpdateAddressBadRequestResponse
      verifyMetricsFailure(metricId)
    }

    "return UpdateAddressUnexpectedResponse when an unexpected status is returned" in new LocalSetup {
      stubPost(url, IM_A_TEAPOT, Some(requestBody), None)

      val result: UpdateAddressResponse = service.updateAddress(nino, etag, address).futureValue
      result mustBe a[UpdateAddressUnexpectedResponse]
      result.toString mustBe "UpdateAddressUnexpectedResponse(HttpResponse status=418)"
      verifyMetricsFailure(metricId)
    }

    "return UpdateAddressErrorResponse when Citizen Details service returns an exception" in new LocalSetup {
      val delay: Int = 5000
      stubWithDelay(url, OK, None, None, delay)

      val result: UpdateAddressResponse = service.updateAddress(nino, etag, address).futureValue
      result mustBe UpdateAddressErrorResponse(_: GatewayTimeoutException)
      verifyMetricsFailure(metricId)
    }
  }

  "Calling CitizenDetailsService.getMatchingDetails" must {

    trait LocalSetup extends SpecSetup {
      val metricId    = "get-matching-details"
      def url: String = s"/citizen-details/nino/$nino"
    }

    "return MatchingDetailsSuccessResponse containing an SAUTR when the service returns an SAUTR" in new LocalSetup {
      val saUtr: String                   = new SaUtrGenerator().nextSaUtr.utr
      stubGet(url, OK, Some(Json.obj("ids" -> Json.obj("sautr" -> saUtr)).toString()))

      val result: MatchingDetailsResponse = service.getMatchingDetails(nino).futureValue
      result mustBe MatchingDetailsSuccessResponse(MatchingDetails(Some(SaUtr(saUtr))))
      verifyMetricsSuccess(metricId)
    }

    "return MatchingDetailsSuccessResponse containing no SAUTR when the service does not return an SAUTR" in new LocalSetup {
      stubGet(url, OK, Some(Json.obj("ids" -> Json.obj("sautr" -> JsNull)).toString()))

      val result: MatchingDetailsResponse = service.getMatchingDetails(nino).futureValue
      result mustBe MatchingDetailsSuccessResponse(MatchingDetails(None))
      verifyMetricsSuccess(metricId)
    }

    "return MatchingDetailsNotFoundResponse when citizen-details returns an 404" in new LocalSetup {
      stubGet(url, NOT_FOUND, None)

      val result: MatchingDetailsResponse = service.getMatchingDetails(nino).futureValue
      result mustBe MatchingDetailsNotFoundResponse
      verifyMetricsFailure(metricId)
    }

    "return MatchingDetailsUnexpectedResponse when citizen-details returns an unexpected response code" in new LocalSetup {
      stubGet(url, IM_A_TEAPOT, None)

      val result: MatchingDetailsResponse = service.getMatchingDetails(nino).futureValue
      result mustBe a[MatchingDetailsUnexpectedResponse]
      result.toString mustBe "MatchingDetailsUnexpectedResponse(HttpResponse status=418)"
      verifyMetricsFailure(metricId)
    }

    "return MatchingDetailsErrorResponse when hod call results in another exception" in new LocalSetup {
      val delay: Int = 5000
      stubWithDelay(url, OK, None, None, delay)

      val result: MatchingDetailsResponse = await(service.getMatchingDetails(nino))
      result mustBe MatchingDetailsErrorResponse(_: GatewayTimeoutException)
      verifyMetricsFailure(metricId)
    }
  }

  "Calling CitizenDetailsService.getEtag" must {

    trait LocalSetup extends SpecSetup {
      val metricId    = "get-etag"
      def url: String = s"/citizen-details/$nino/etag"
    }

    "return an etag when citizen-details returns 200" in new LocalSetup {
      stubGet(url, OK, Some(JsObject(Seq(("etag", JsString("115")))).toString()))

      val result: Option[ETag] = service.getEtag(nino.nino).futureValue
      result mustBe Some(ETag("115"))
      verifyMetricsSuccess(metricId)
    }

    "return None" when {
      "citizen-details returns 404" in new LocalSetup {
        stubGet(url, NOT_FOUND, None)

        val result: Option[ETag] = service.getEtag(nino.nino).futureValue
        result mustBe None
        verifyMetricsFailure(metricId)
      }

      "citizen-details returns 423" in new LocalSetup {
        stubGet(url, LOCKED, None)

        val result: Option[ETag] = service.getEtag(nino.nino).futureValue
        result mustBe None
        verifyMetricsFailure(metricId)
      }

      "citizen-details returns 500" in new LocalSetup {
        stubGet(url, INTERNAL_SERVER_ERROR, None)

        val result: Option[ETag] = service.getEtag(nino.nino).futureValue
        result mustBe None
        verifyMetricsFailure(metricId)
      }

      "the call to citizen-details throws an exception" in new LocalSetup {
        val delay: Int = 5000
        stubWithDelay(url, OK, None, None, delay)

        val result: Option[ETag] = service.getEtag(nino.nino).futureValue
        result mustBe None
        verifyMetricsFailure(metricId)
      }
    }
  }
}
