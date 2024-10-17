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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Application
import play.api.http.Status._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.Helpers.{GET, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import play.api.test.FakeRequest
import testUtils.IntegrationSpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.SessionKeys

import java.util.UUID
import scala.annotation.tailrec
import scala.concurrent.Future

class HomeControllerTrustedHelperISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.taxcalc-frontend.port" -> server.port(),
      "microservice.services.tai.port"              -> server.port(),
      "microservice.services.pertax.port"           -> server.port()
    )
    .build()

  val url          = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString
  val pertaxUrl    = s"/pertax/authorise"
  val authUrl      = s"/auth/authorise"

  @tailrec
  private def generateHelperNino: Nino = {
    val nino = new Generator().nextNino
    if (nino == generatedNino)
      generateHelperNino
    else {
      nino
    }
  }

  val generatedHelperNino: Nino = generateHelperNino

  val authTrustedHelperResponse: String =
    s"""
       |{
       |    "confidenceLevel": 200,
       |    "nino": "$generatedNino",
       |    "name": {
       |        "name": "John",
       |        "lastName": "Smith"
       |    },
       |    "trustedHelper": {
       |        "principalName": "principal",
       |        "attorneyName": "attorney",
       |        "returnLinkUrl": "returnLink",
       |        "principalNino": "$generatedHelperNino"
       |     },
       |    "loginTimes": {
       |        "currentLogin": "2021-06-07T10:52:02.594Z",
       |        "previousLogin": null
       |    },
       |    "optionalCredentials": {
       |        "providerId": "4911434741952698",
       |        "providerType": "GovernmentGateway"
       |    },
       |    "authProviderId": {
       |        "ggCredId": "xyz"
       |    },
       |    "externalId": "testExternalId",
       |    "allEnrolments": [
       |       {
       |          "key":"HMRC-PT",
       |          "identifiers": [
       |             {
       |                "key":"NINO",
       |                "value": "$generatedNino"
       |             }
       |          ]
       |       }
       |    ],
       |    "affinityGroup": "Individual",
       |    "credentialStrength": "strong"
       |}
       |""".stripMargin

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController(memorandum = false)

    server.stubFor(
      post(urlEqualTo("/pertax/authorise"))
        .willReturn(
          aResponse()
            .withBody("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}")
        )
    )

    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authTrustedHelperResponse)))
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedHelperNino")).willReturn(ok(citizenResponse)))
  }

  "personal-account" must {

    "show the home page when helping someone" in {

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      server.verify(1, postRequestedFor(urlEqualTo(pertaxUrl)))
      server.verify(1, getRequestedFor(urlEqualTo(s"/citizen-details/nino/$generatedHelperNino")))

    }
  }
}
