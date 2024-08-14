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
import com.github.tomakehurst.wiremock.client.WireMock._
import config.ConfigDecorator
import models.addresslookup.{AddressRecord, RecordSet}
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import testUtils.Fixtures.{oneAndTwoOtherPlacePafRecordSet, twoOtherPlaceRecordSet}
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future
import scala.io.Source

class AddressLookupConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.address-lookup.port" -> server.port(),
      "auditing.enabled"                          -> false
    )
    .build()

  def addressLookupConnector: AddressLookupConnector = inject[AddressLookupConnector]

  private val mockHttpClientResponse: HttpClientResponse = mock[HttpClientResponse]

  private val mockHttpClientV2: HttpClientV2 = mock[HttpClientV2]

  private val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  private val mockConfigDecorator = mock[ConfigDecorator]

  private val dummyContent = "error message"

  val urlPost = "/lookup"

  val requestBody: JsObject = Json.obj(
    "postcode" -> "ZZ11ZZ",
    "filter"   -> "2"
  )

  val addressRecordSet: String =
    Source.fromInputStream(getClass.getResourceAsStream("/address-lookup/recordSet.json")).mkString

  val missingAddressLineRecordSet: String =
    Source
      .fromInputStream(getClass.getResourceAsStream("/address-lookup/recordSetWithMissingAddressLines.json"))
      .mkString

  val emptyRecordSet = "[]"

  "AddressLookupService" when {
    "lookup is called as post" should {

      "return a List of addresses matching the given postcode, if any matching record exists" in {

        val wholeStreetRequestBody: JsObject = Json.obj(
          "postcode" -> "ZZ11ZZ"
        )

        server.stubFor(
          post(urlEqualTo(urlPost))
            .withRequestBody(equalToJson(wholeStreetRequestBody.toString))
            .willReturn(ok(addressRecordSet))
        )

        val result = addressLookupConnector
          .lookup("ZZ11ZZ", None)
          .value
          .futureValue
          .getOrElse(Json.toJson(emptyRecordSet).as[List[AddressRecord]])

        result mustBe oneAndTwoOtherPlacePafRecordSet

        server.verify(
          postRequestedFor(urlEqualTo(urlPost))
            .withHeader("X-Hmrc-Origin", equalTo("PERTAX"))
        )
      }

      "return a List of addresses matching the given postcode and house number, if any matching record exists" in {

        server.stubFor(
          post(urlEqualTo(urlPost))
            .withRequestBody(equalToJson(requestBody.toString))
            .willReturn(ok(addressRecordSet))
        )

        val result = addressLookupConnector
          .lookup("ZZ11ZZ", Some("2"))
          .value
          .futureValue
          .getOrElse(RecordSet(Json.toJson(emptyRecordSet).as[List[AddressRecord]]))

        result mustBe oneAndTwoOtherPlacePafRecordSet
      }
    }

    "return a List of addresses, filtering out addresses with no lines" in {

      server.stubFor(
        post(urlEqualTo(urlPost))
          .withRequestBody(equalToJson(requestBody.toString))
          .willReturn(ok(missingAddressLineRecordSet))
      )

      val result =
        addressLookupConnector
          .lookup("ZZ11ZZ", Some("2"))
          .value
          .futureValue
          .getOrElse(oneAndTwoOtherPlacePafRecordSet)
      result mustBe twoOtherPlaceRecordSet
    }

    "return an empty response for the given house name/number and postcode, if matching record doesn't exist" in {

      server.stubFor(
        post(urlEqualTo(urlPost))
          .withRequestBody(equalToJson(requestBody.toString))
          .willReturn(ok(emptyRecordSet))
      )

      val result =
        addressLookupConnector.lookup("ZZ11ZZ", Some("2")).value.futureValue.getOrElse(addressRecordSet)
      result mustBe RecordSet(Seq.empty[AddressRecord])
    }

    List(
      SERVICE_UNAVAILABLE,
      IM_A_TEAPOT,
      BAD_REQUEST
    ).foreach { httpResponse =>
      s"return $httpResponse response when called and service returns not found" in {

        server.stubFor(
          post(urlEqualTo(urlPost))
            .withRequestBody(equalToJson(requestBody.toString))
            .willReturn(aResponse().withStatus(httpResponse))
        )

        val result = addressLookupConnector.lookup("ZZ11ZZ", Some("2"))

        result.value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe httpResponse
      }
    }

    List(
      TOO_MANY_REQUESTS,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      NOT_FOUND,
      UNPROCESSABLE_ENTITY
    ).foreach { httpResponse =>
      s"return $httpResponse response from HttpClientResponse service returns not found" in {

        when(mockHttpClientResponse.read(any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future(Left(UpstreamErrorResponse(dummyContent, httpResponse)))
          )
        )

        when(mockHttpClientV2.post(any())(any())).thenReturn(mockRequestBuilder)

        when(mockRequestBuilder.withBody(any())(any(), any(), any()))
          .thenReturn(mockRequestBuilder)
        when(mockRequestBuilder.transform(any()))
          .thenReturn(mockRequestBuilder)

        def addressLookupConnectorWithMock: AddressLookupConnector = new AddressLookupConnector(
          mockConfigDecorator,
          mockHttpClientV2,
          inject[ServicesConfig],
          mockHttpClientResponse
        )

        val result = addressLookupConnectorWithMock.lookup("ZZ11ZZ", Some("2"))

        result.value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe httpResponse
      }
    }
  }
}
