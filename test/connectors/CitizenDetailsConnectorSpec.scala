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

import cats.data.EitherT
import config.ConfigDecorator
import models._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.libs.json.{JsNull, JsObject, JsString, JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers.GET
import play.api.test.{DefaultAwaitTimeout, FakeRequest, Injecting}
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino, SaUtrGenerator}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import scala.concurrent.Future
import scala.util.Random

class CitizenDetailsConnectorSpec
    extends ConnectorSpec
    with WireMockHelper
    with DefaultAwaitTimeout
    with Injecting
    with BeforeAndAfterEach {

  private val mockHttpClientResponse: HttpClientResponse = mock[HttpClientResponse]

  private val mockHttpClientV2: HttpClientV2 = mock[HttpClientV2]

  private val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  private val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  private val dummyContent = "error message"

  override implicit lazy val app: Application = app(
    Map("microservice.services.citizen-details.port" -> server.port())
  )

  val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/")

  trait SpecSetup {

    def url: String

    val personDetails: String =
      """
        |{
        |  "person": {
        |    "firstName": "Firstname",
        |    "middleName": "Middlename",
        |    "lastName": "Lastname",
        |    "initials": "FML",
        |    "title": "Dr",
        |    "honours": "Phd.",
        |    "sex": "M",
        |    "dateOfBirth": "1945-03-18",
        |    "nino": "RC367466B",
        |    "status":1
        |  },
        |  "address": {
        |    "line1": "1 Fake Street",
        |    "line2": "Fake Town",
        |    "line3": "Fake City",
        |    "line4": "Fake Region",
        |    "postcode": "AA1 1AA",
        |    "startDate": "2015-03-15",
        |    "type": "Residential",
        |    "status":1
        |  }
        |}
        |""".stripMargin

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

    lazy val connector: CitizenDetailsConnector = {
      val httpClient    = app.injector.instanceOf[HttpClientV2]
      val serviceConfig = app.injector.instanceOf[ServicesConfig]
      new DefaultCitizenDetailsConnector(httpClient, inject[ConfigDecorator], serviceConfig, inject[HttpClientResponse])
    }
  }

  "Calling personDetails" must {

    trait LocalSetup extends SpecSetup {
      def url: String = s"/citizen-details/$nino/designatory-details"
    }

    "return OK when called with an existing nino" in new LocalSetup {
      stubGet(url, OK, Some(personDetails))

      val result: Either[UpstreamErrorResponse, JsValue] =
        connector.personDetails(nino).value.futureValue

      result mustBe a[Right[_, _]]
      result.getOrElse(null) mustBe Json.parse(personDetails)
    }

    "return NOT_FOUND when called with an unknown nino" in new LocalSetup {
      stubGet(url, NOT_FOUND, None)

      val result: Int =
        connector.personDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe NOT_FOUND
    }

    "return LOCKED when a locked hidden record (MCI) is asked for" in new LocalSetup {
      stubGet(url, LOCKED, None)

      val result: Int =
        connector.personDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe LOCKED
    }

    "return given status code when an unexpected status is returned" in new LocalSetup {
      stubGet(url, IM_A_TEAPOT, None)

      val result: Int =
        connector.personDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe IM_A_TEAPOT
    }
  }

  "calling updateAddress" must {

    trait LocalSetup extends SpecSetup {
      def url: String = s"/citizen-details/$nino/designatory-details/address"

      val etag: String        = "115"
      val requestBody: String = Json.obj("etag" -> etag, "address" -> Json.toJson(address)).toString()
    }

    "return CREATED when called with valid Nino and address data" in new LocalSetup {
      stubPost(url, CREATED, Some(requestBody), None)

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector
          .updateAddress(nino, etag, address)
          .value
          .futureValue
      result mustBe a[Right[_, _]]
      result.getOrElse(HttpResponse(BAD_REQUEST, "")).status mustBe CREATED
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

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector
          .updateAddress(nino, etag, correspondenceAddress)
          .value
          .futureValue

      result mustBe a[Right[_, _]]
      result.getOrElse(HttpResponse(BAD_REQUEST, "")).status mustBe CREATED
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
      def url: String = s"/citizen-details/nino/$nino"
    }

    "return OK containing an SAUTR when the service returns an SAUTR" in new LocalSetup {
      val saUtr: String                                       = new SaUtrGenerator().nextSaUtr.utr
      stubGet(url, OK, Some(Json.obj("ids" -> Json.obj("sautr" -> saUtr)).toString()))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.getMatchingDetails(nino).value.futureValue

      result mustBe a[Right[_, _]]
      result.getOrElse(HttpResponse(BAD_REQUEST, "")).status mustBe OK
    }

    "return OK containing no SAUTR when the service does not return an SAUTR" in new LocalSetup {
      stubGet(url, OK, Some(Json.obj("ids" -> Json.obj("sautr" -> JsNull)).toString()))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.getMatchingDetails(nino).value.futureValue

      result mustBe a[Right[_, _]]
      result.getOrElse(HttpResponse(BAD_REQUEST, "")).status mustBe OK
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
      def url: String = s"/citizen-details/$nino/etag"
    }

    "return an etag when citizen-details returns 200" in new LocalSetup {
      stubGet(url, OK, Some(JsObject(Seq(("etag", JsString("115")))).toString()))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.getEtag(nino.nino).value.futureValue

      result mustBe a[Right[_, _]]
      result.getOrElse(HttpResponse(BAD_REQUEST, "")).status mustBe OK

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

      "citizen-details HttpClientResponse returns 500" in new LocalSetup {

        when(mockHttpClientResponse.read(any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future(Left(UpstreamErrorResponse(dummyContent, INTERNAL_SERVER_ERROR)))
          )
        )

        when(mockHttpClientV2.get(any())(any())).thenReturn(mockRequestBuilder)

        when(mockRequestBuilder.transform(any()))
          .thenReturn(mockRequestBuilder)

        def citizenDetailsConnectorWithMock: CitizenDetailsConnector = new DefaultCitizenDetailsConnector(
          mockHttpClientV2,
          mockConfigDecorator,
          inject[ServicesConfig],
          mockHttpClientResponse
        )

        val result: Int = citizenDetailsConnectorWithMock
          .getEtag(nino.nino)
          .value
          .futureValue
          .left
          .getOrElse(UpstreamErrorResponse("", OK))
          .statusCode
        result mustBe INTERNAL_SERVER_ERROR
      }
    }

    "return BAD_GATEWAY when the call to citizen-details throws an exception" in new LocalSetup {

      when(mockHttpClientResponse.read(any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future(Left(UpstreamErrorResponse(dummyContent, BAD_GATEWAY)))
        )
      )

      when(mockHttpClientV2.get(any())(any())).thenReturn(mockRequestBuilder)

      when(mockRequestBuilder.transform(any()))
        .thenReturn(mockRequestBuilder)

      def citizenDetailsConnectorWithMock: CitizenDetailsConnector = new DefaultCitizenDetailsConnector(
        mockHttpClientV2,
        mockConfigDecorator,
        inject[ServicesConfig],
        mockHttpClientResponse
      )

      val result: Int = citizenDetailsConnectorWithMock
        .getEtag(nino.nino)
        .value
        .futureValue
        .left
        .getOrElse(UpstreamErrorResponse("", OK))
        .statusCode
      result mustBe BAD_GATEWAY
    }
  }
}

class CitizenDetailsConnectorTimeoutSpec
    extends ConnectorSpec
    with WireMockHelper
    with DefaultAwaitTimeout
    with Injecting
    with BeforeAndAfterEach {

  override implicit lazy val app: Application = app(
    Map(
      "microservice.services.citizen-details.port"                  -> server.port(),
      "microservice.services.citizen-details.timeoutInMilliseconds" -> 1
    )
  )

  val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/")

  trait SpecSetup {

    def url: String

    lazy val connector: CitizenDetailsConnector = {
      val httpClient      = app.injector.instanceOf[HttpClientV2]
      val serviceConfig   = app.injector.instanceOf[ServicesConfig]
      val configDecorator = app.injector.instanceOf[ConfigDecorator]
      new DefaultCitizenDetailsConnector(httpClient, configDecorator, serviceConfig, inject[HttpClientResponse])
    }
  }

  "Calling personDetails" must {
    trait LocalSetup extends SpecSetup {
      def url: String = s"/citizen-details/$nino/designatory-details"
    }

    "return bad gateway when the call to retrieve person details results in a timeout" in new LocalSetup {
      stubWithDelay(url, OK, None, None, 100)
      val result: Int =
        connector.personDetails(nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
      result mustBe BAD_GATEWAY
    }
  }
}
