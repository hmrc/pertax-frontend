package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{SingleAccountCheckToggle, TaxComponentsToggle, TaxcalcToggle}
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsFormUrlEncoded}
import services.admin.FeatureFlagService
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap

class PostcodeLookupControllerISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"        -> true,
      "feature.breathing-space-indicator.timeoutInSec"   -> 4,
      "microservice.services.taxcalc.port"               -> server.port(),
      "microservice.services.tai.port"                   -> server.port(),
      "microservice.services.enrolment-store-proxy.port" -> server.port()
    )
    .build()

  val apiUrl = "/personal-account/your-address/postal/find-address"

  def request[A]: Request[A] =
    FakeRequest("POST", apiUrl)
      .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
      .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      .asInstanceOf[Request[A]]

  val urlPost = "/lookup"

  val personDetailsUrl = s"/citizen-details/$generatedNino/designatory-details"

  val addressRecordSet: String =
    s"""
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
  """

  override def beforeEach(): Unit = {
    server.resetAll()
    beforeEachHomeController()

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

  }

  "personal-account" must {
    "show manual address form when the address times out after 5 secs" in {

      val wholeStreetRequestBody: JsObject = Json.obj(
        "postcode" -> "AA1 1AA"
      )

      server.stubFor(
        post(urlEqualTo(urlPost))
          .withRequestBody(equalToJson(wholeStreetRequestBody.toString))
          .willReturn(ok(addressRecordSet).withFixedDelay(7000))
      )

      val result = route(app, request).get
      contentAsString(result) must include("enter your address manually")
    }

  }
}
