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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import models.addresslookup.RecordSet
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.http.Status.NOT_FOUND
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import util.Fixtures.{oneAndTwoOtherPlacePafRecordSet, twoOtherPlaceRecordSet}
import util.{BaseSpec, WireMockHelper}

import scala.io.Source

class AddressLookupServiceSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.address-lookup.port" -> server.port(),
      "auditing.enabled"                          -> false
    )
    .build()

  def addressLookupService: AddressLookupService = injected[AddressLookupService]

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

        addressLookupService.lookup("ZZ11ZZ", None).futureValue mustBe
          AddressLookupSuccessResponse(oneAndTwoOtherPlacePafRecordSet)

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

        addressLookupService.lookup("ZZ11ZZ", Some("2")).futureValue mustBe
          AddressLookupSuccessResponse(oneAndTwoOtherPlacePafRecordSet)

      }
    }

    "return a List of addresses, filtering out addresses with no lines" in {

      server.stubFor(
        post(urlEqualTo(urlPost))
          .withRequestBody(equalToJson(requestBody.toString))
          .willReturn(ok(missingAddressLineRecordSet))
      )

      val result = addressLookupService.lookup("ZZ11ZZ", Some("2"))

      result.futureValue mustBe AddressLookupSuccessResponse(twoOtherPlaceRecordSet)
    }

    "return an empty response for the given house name/number and postcode, if matching record doesn't exist" in {

      server.stubFor(
        post(urlEqualTo(urlPost))
          .withRequestBody(equalToJson(requestBody.toString))
          .willReturn(ok(emptyRecordSet))
      )

      val result = addressLookupService.lookup("ZZ11ZZ", Some("2"))

      result.futureValue mustBe AddressLookupSuccessResponse(RecordSet(List()))
    }

    "return AddressLookupUnexpectedResponse response, when called and service returns not found" in {

      server.stubFor(
        post(urlEqualTo(urlPost))
          .withRequestBody(equalToJson(requestBody.toString))
          .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = addressLookupService.lookup("ZZ11ZZ", Some("2"))

      result.futureValue.asInstanceOf[AddressLookupUnexpectedResponse].r.status mustBe NOT_FOUND
    }

    "return AddressLookupErrorResponse when called and service is down" in {

      server.stubFor(
        post(urlEqualTo(urlPost))
          .withRequestBody(equalToJson(requestBody.toString))
          .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK))
      )

      val result = addressLookupService.lookup("ZZ11ZZ", Some("2"))

      result.futureValue mustBe a[AddressLookupErrorResponse]
    }

  }

}
