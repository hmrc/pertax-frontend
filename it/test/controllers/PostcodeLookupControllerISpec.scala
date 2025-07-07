/*
 * Copyright 2024 HM Revenue & Customs
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

package controllers

import cats.data.EitherT
import com.github.tomakehurst.wiremock.client.WireMock.*
import models.AgentClientStatus
import models.admin.TaxComponentsRetrievalToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, route, writeableOf_AnyContentAsFormUrlEncoded}
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.{SessionKeys, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class PostcodeLookupControllerISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure("microservice.services.address-lookup.port" -> server.port(), "feature.address-lookup.timeoutInSec" -> 1)
    .build()

  val apiUrl = "/personal-account/your-address/postal/find-address"

  def request[A]: Request[A] =
    FakeRequest("POST", apiUrl)
      .withFormUrlEncodedBody("postcode" -> "AA1 1AA", "filter" -> "")
      .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      .asInstanceOf[Request[A]]

  val urlPost = "/lookup"

  val personDetailsUrl = s"/citizen-details/$generatedNino/designatory-details"

  val addressRecordSet: String =
    """|
       |[
       |    {
       |      "id": "GB990091234514",
       |      "address": {
       |        "lines": [
       |          "2 Other Place",
       |          "Some District"
       |        ],
       |        "town": "Anytown",
       |        "postcode": "AA1 1AA",
       |        "country": {
       |          "code": "UK",
       |          "name": "United Kingdom"
       |        },
       |        "subdivision": {
       |          "code": "GB-ENG",
       |          "name": "England"
       |        },
       |        "status": 0
       |      },
       |      "language": "en"
       |    },
       |    {
       |      "id": "GB990091234515",
       |      "address": {
       |        "lines": [
       |          "3 Other Place",
       |          "Some District"
       |        ],
       |        "town": "Anytown",
       |        "postcode": "AA1 1AA",
       |        "country": {
       |          "code": "UK",
       |          "name": "United Kingdom"
       |        },
       |        "subdivision":{
       |          "code": "GB-SCT",
       |          "name": "Scotland"
       |        },
       |        "status": 0
       |      },
       |      "language": "en"
       |    }
       |  ]
       |""".stripMargin

  val designatoryDetails: String =
    """|
     |{
       |  "etag" : "115",
       |  "person" : {
       |    "firstName" : "HIPPY",
       |    "middleName" : "T",
       |    "lastName" : "NEWYEAR",
       |    "title" : "Mr",
       |    "honours": "BSC",
       |    "sex" : "M",
       |    "dateOfBirth" : "1952-04-01",
       |    "nino" : "TW189213B",
       |    "deceased" : false
       |  },
       |  "address" : {
       |    "line1" : "26 FARADAY DRIVE",
       |    "line2" : "PO BOX 45",
       |    "line3" : "LONDON",
       |    "postcode" : "CT1 1RQ",
       |    "startDate": "2009-08-29",
       |    "country" : "GREAT BRITAIN",
       |    "type" : "Residential",
       |    "status": 1
       |  }
       |}
       |""".stripMargin

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController()

    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(ok(designatoryDetails))
    )

    server.stubFor(
      get(urlEqualTo(s"/agent-client-relationships/customer-status"))
        .willReturn(
          ok(
            Json
              .toJson(
                AgentClientStatus(
                  hasPendingInvitations = true,
                  hasInvitationsHistory = true,
                  hasExistingRelationships = true
                )
              )
              .toString
          )
        )
    )

    server.stubFor(
      get(urlEqualTo(personDetailsUrl))
        .willReturn(
          ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino))
        )
    )

    when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
      .thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = false))
      )
  }

  "personal-account" must {
    "show edit address when the address service times out after 5 secs" in {

      val wholeStreetRequestBody: JsObject = Json.obj(
        "postcode" -> "AA11AA"
      )

      server.stubFor(
        post(urlEqualTo(urlPost))
          .withRequestBody(equalToJson(wholeStreetRequestBody.toString))
          .willReturn(ok(addressRecordSet).withFixedDelay(2000))
      )

      val result = route(app, request)
      result.map(redirectLocation) mustBe Some(Some("/personal-account/your-address/postal/edit-address"))
    }

    "show select address when the address service returns the set of addresses" in {

      val wholeStreetRequestBody: JsObject = Json.obj(
        "postcode" -> "AA11AA"
      )

      server.stubFor(
        post(urlEqualTo(urlPost))
          .withRequestBody(equalToJson(wholeStreetRequestBody.toString))
          .willReturn(ok(addressRecordSet))
      )

      val result = route(app, request)
      result.map(redirectLocation) mustBe Some(Some("/personal-account/your-address/postal/select-address"))
    }
  }
}
