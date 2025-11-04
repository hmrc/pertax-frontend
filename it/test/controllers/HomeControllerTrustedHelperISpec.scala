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
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import models.admin.GetPersonFromCitizenDetailsToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.*
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, status as httpStatus, writeableOf_AnyContentAsEmpty}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.{SessionKeys, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.util.UUID
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
       |        "principalName": "principal Name",
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

  private def setupWrapperData(attorneyBannerPresent: Boolean): StubMapping =
    if (attorneyBannerPresent) {
      server.stubFor(
        WireMock
          .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
          .willReturn(ok(singleAccountWrapperDataResponseWithTrustedHelper))
      )
    } else {
      server.stubFor(
        WireMock
          .get(urlMatching("/single-customer-account-wrapper-data/wrapper-data.*"))
          .willReturn(ok(singleAccountWrapperDataResponse))
      )
    }

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

    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedHelperNino/designatory-details?cached=true"))
        .willReturn(ok(personDetailsResponse(generatedHelperNino.nino)))
    )

    when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
      .thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true))
      )

  }

  "personal-account" must {

    "show the home page when helping someone - trusted helper" in {
      setupWrapperData(attorneyBannerPresent = true)
      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result) must include("principal Name")
      server.verify(1, postRequestedFor(urlEqualTo(pertaxUrl)))
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/citizen-details/$generatedHelperNino/designatory-details?cached=true"))
      )

    }
    "show the home page when helping someone - no trusted helper" in {
      setupWrapperData(attorneyBannerPresent = false)
      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result) must not include "principal Name"
    }
  }
}
