/*
 * Copyright 2023 HM Revenue & Customs
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

import models._
import play.api.Application
import play.api.libs.json.{JsNull, JsObject, JsString, Json}
import play.api.test.{DefaultAwaitTimeout, Injecting}
import testUtils.{Fixtures, WireMockHelper}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtrGenerator}
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import scala.util.Random

class CitizenDetailsConnectorSpec
    extends ConnectorSpec
    with WireMockHelper
    with MockitoSugar
    with DefaultAwaitTimeout
    with Injecting {

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

    lazy val connector = {
      val httpClient    = app.injector.instanceOf[HttpClient]
      val serviceConfig = app.injector.instanceOf[ServicesConfig]
      new CitizenDetailsConnector(httpClient, serviceConfig, inject[HttpClientResponse])
    }
  }

  "Calling personDetails" must {

    trait LocalSetup extends SpecSetup {
      val metricId    = "get-person-details"
      def url: String = s"/citizen-details/$nino/designatory-details"
    }

    "return OK when called with an existing nino" in new LocalSetup {
      stubGet(url, OK, Some(Json.toJson(personDetails).toString()))

      val result: Int =
        connector.personDetails(nino).value.futureValue.right.getOrElse(HttpResponse(BAD_REQUEST, "")).status
      result mustBe OK
    }

    "return NOT_FOUND when called with an unknown nino" in new LocalSetup {
      stubGet(url, NOT_FOUND, None)

      val result =
        connector.personDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe NOT_FOUND
    }

    "return LOCKED when a locked hidden record (MCI) is asked for" in new LocalSetup {
      stubGet(url, LOCKED, None)

      val result =
        connector.personDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe LOCKED
    }

    "return given status code when an unexpected status is returned" in new LocalSetup {
      stubGet(url, IM_A_TEAPOT, None)

      val result =
        connector.personDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe IM_A_TEAPOT
    }

    "return BAD_GATEWAY when the call to retrieve person details results in an exception" in new LocalSetup {
      val delay: Int = 5000
      stubWithDelay(url, OK, None, None, delay)

      val result: Int =
        connector.personDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe BAD_GATEWAY
    }
  }

  "calling updateAddress" must {

    trait LocalSetup extends SpecSetup {
      val metricId            = "update-address"
      def url: String         = s"/citizen-details/$nino/designatory-details/address"
      val etag: String        = "115"
      val requestBody: String = Json.obj("etag" -> etag, "address" -> Json.toJson(address)).toString()
    }

    "return CREATED when called with valid Nino and address data" in new LocalSetup {
      stubPost(url, CREATED, Some(requestBody), None)

      val result: Int =
        connector
          .updateAddress(nino, etag, address)
          .value
          .futureValue
          .right
          .getOrElse(HttpResponse(BAD_REQUEST, ""))
          .status
      result mustBe CREATED
    }

    "return CREATED when called with a valid Nino and valid correspondence address with an end date" in new LocalSetup {
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

      val result: Int =
        connector
          .updateAddress(nino, etag, correspondenceAddress)
          .value
          .futureValue
          .right
          .getOrElse(HttpResponse(BAD_REQUEST, ""))
          .status
      result mustBe CREATED
    }

    "return BAD_REQUEST when Citizen Details service returns BAD_REQUEST" in new LocalSetup {
      stubPost(url, BAD_REQUEST, Some(requestBody), None)

      val result: Int =
        connector
          .updateAddress(nino, etag, address)
          .value
          .futureValue
          .left
          .getOrElse(UpstreamErrorResponse("", OK))
          .statusCode
      result mustBe BAD_REQUEST
    }

    "return given status code when an unexpected status is returned" in new LocalSetup {
      stubPost(url, IM_A_TEAPOT, Some(requestBody), None)

      val result: Int =
        connector
          .updateAddress(nino, etag, address)
          .value
          .futureValue
          .left
          .getOrElse(UpstreamErrorResponse("", OK))
          .statusCode
      result mustBe IM_A_TEAPOT
    }

    "return BAD_GATEWAY when the call to UpdateAddressErrorResponse when Citizen Details service returns an exception" in new LocalSetup {
      val delay: Int = 5000
      stubWithDelay(url, OK, None, None, delay)

      val result: Int = connector
        .updateAddress(nino, etag, address)
        .value
        .futureValue
        .left
        .getOrElse(UpstreamErrorResponse("", OK))
        .statusCode
      result mustBe BAD_GATEWAY
    }
  }

  "Calling getMatchingDetails" must {

    trait LocalSetup extends SpecSetup {
      val metricId    = "get-matching-details"
      def url: String = s"/citizen-details/nino/$nino"
    }

    "return OK containing an SAUTR when the service returns an SAUTR" in new LocalSetup {
      val saUtr: String = new SaUtrGenerator().nextSaUtr.utr
      stubGet(url, OK, Some(Json.obj("ids" -> Json.obj("sautr" -> saUtr)).toString()))

      val result: Int   =
        connector.getMatchingDetails(nino).value.futureValue.right.getOrElse(HttpResponse(BAD_REQUEST, "")).status
      result mustBe OK
    }

    "return OK containing no SAUTR when the service does not return an SAUTR" in new LocalSetup {
      stubGet(url, OK, Some(Json.obj("ids" -> Json.obj("sautr" -> JsNull)).toString()))

      val result: Int =
        connector.getMatchingDetails(nino).value.futureValue.right.getOrElse(HttpResponse(BAD_REQUEST, "")).status
      result mustBe OK
    }

    "return NOT_FOUND when citizen-details returns an 404" in new LocalSetup {
      stubGet(url, NOT_FOUND, None)

      val result: Int =
        connector.getMatchingDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe NOT_FOUND
    }

    "return given status code when citizen-details returns an unexpected response code" in new LocalSetup {
      stubGet(url, IM_A_TEAPOT, None)

      val result: Int =
        connector.getMatchingDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe IM_A_TEAPOT
    }

    "return BAD_GATEWAY when the call to MatchingDetailsErrorResponse when hod call results in another exception" in new LocalSetup {
      val delay: Int = 5000
      stubWithDelay(url, OK, None, None, delay)

      val result: Int =
        connector.getMatchingDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe BAD_GATEWAY
    }
  }

  "Calling getEtag" must {

    trait LocalSetup extends SpecSetup {
      val metricId    = "get-etag"
      def url: String = s"/citizen-details/$nino/etag"
    }

    "return an etag when citizen-details returns 200" in new LocalSetup {
      stubGet(url, OK, Some(JsObject(Seq(("etag", JsString("115")))).toString()))

      val result: Int =
        connector.getEtag(nino.nino).value.futureValue.right.getOrElse(HttpResponse(BAD_REQUEST, "")).status
      result mustBe OK

    }

    "return None" when {
      "citizen-details returns 404" in new LocalSetup {
        stubGet(url, NOT_FOUND, None)

        val result: Int =
          connector.getEtag(nino.nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
        result mustBe NOT_FOUND
      }

      "citizen-details returns 423" in new LocalSetup {
        stubGet(url, LOCKED, None)

        val result: Int =
          connector.getEtag(nino.nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
        result mustBe LOCKED
      }

      "citizen-details returns 500" in new LocalSetup {
        stubGet(url, INTERNAL_SERVER_ERROR, None)

        val result: Int = connector
          .getEtag(nino.nino)
          .value
          .futureValue
          .left
          .getOrElse(UpstreamErrorResponse("", OK))
          .statusCode
        result mustBe INTERNAL_SERVER_ERROR
      }

      "return BAD_GATEWAY when the call to citizen-details throws an exception" in new LocalSetup {
        val delay: Int = 5000
        stubWithDelay(url, OK, None, None, delay)

        val result: Int =
          connector.getEtag(nino.nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
        result mustBe BAD_GATEWAY
      }
    }
  }
}
