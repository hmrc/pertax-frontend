package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.AgentClientStatus
import models.admin.{RlsInterruptToggle, SingleAccountCheckToggle, TaxComponentsToggle, TaxcalcToggle}
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, route, writeableOf_AnyContentAsFormUrlEncoded}
import services.admin.FeatureFlagService
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap

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
    server.resetAll()
    beforeEachHomeController()

    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(ok(designatoryDetails))
    )

    server.stubFor(
      get(urlEqualTo(s"/agent-client-authorisation/status"))
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
      put(urlMatching(s"/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
    )

    server.stubFor(
      get(urlEqualTo(personDetailsUrl))
        .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
    )

    lazy val featureFlagService = app.injector.instanceOf[FeatureFlagService]
    featureFlagService.set(TaxcalcToggle, enabled = false).futureValue
    featureFlagService.set(SingleAccountCheckToggle, enabled = true).futureValue
    featureFlagService.set(TaxComponentsToggle, enabled = true).futureValue
    featureFlagService.set(RlsInterruptToggle, enabled = false).futureValue
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

    "show select address when the address service returns the set of adresses" in {

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