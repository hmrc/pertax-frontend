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

import com.github.tomakehurst.wiremock.client.WireMock._
import models.addresslookup.{AddressRecord, RecordSet}
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import testUtils.Fixtures.{oneAndTwoOtherPlacePafRecordSet, twoOtherPlaceRecordSet}
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.io.Source

class AddressLookupConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.address-lookup.port" -> server.port(),
      "auditing.enabled"                          -> false
    )
    .build()

  def addressLookupConnector: AddressLookupConnector = injected[AddressLookupConnector]

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
      TOO_MANY_REQUESTS,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE,
      IM_A_TEAPOT,
      NOT_FOUND,
      BAD_REQUEST,
      UNPROCESSABLE_ENTITY
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
  }
}
