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
import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.TaxComponentsRetrievalToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.util.UUID
import scala.concurrent.Future

class HomeControllerSelfAssessmentISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.taxcalc-frontend.port"             -> server.port(),
      "microservice.services.tai.port"                          -> server.port(),
      "microservice.services.enrolment-store-proxy.port"        -> server.port(),
      "microservice.services.self-employed-income-support.port" -> server.port()
    )
    .build()

  val url          = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController(auth = false, matchingDetails = false)
    server.stubFor(
      get(urlMatching(s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$generatedUtr/users"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
    )

    when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
      .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true)))
  }

  "self-assessment-home" must {
    "show SaUtr and Request Access message when user has an SaUtr in the matching details body but not the auth body" in {

      val citizenResponse =
        s"""|
           |{
            |  "name": {
            |    "current": {
            |      "firstName": "John",
            |      "lastName": "Smith"
            |    },
            |    "previous": []
            |  },
            |  "ids": {
            |    "nino": "$generatedNino",
            |    "sautr": "$generatedUtr"
            |  },
            |  "dateOfBirth": "11121971"
            |}
            |""".stripMargin

      val authResponse =
        s"""
           |{
           |    "confidenceLevel": 200,
           |    "nino": "$generatedNino",
           |    "sautr": "$generatedUtr",
           |    "name": {
           |        "name": "John",
           |        "lastName": "Smith"
           |    },
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
           |       },
           |       {
           |            "key":"HMRC-MTD-IT",
           |            "identifiers": [
           |                {
           |                    "key":"MTDITID",
           |                    "value": "$generatedUtr"
           |                }
           |            ],
           |            "state": "Activated"
           |        }
           |    ],
           |    "affinityGroup": "Individual",
           |    "credentialStrength": "strong"
           |}
           |""".stripMargin

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(Messages("label.home_page.utr")) mustBe true
      contentAsString(result).contains(generatedUtr) mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains(Messages("label.not_enrolled.link.text")) mustBe true
    }

    "show SaUtr and Activate your Self Assessment message when user has an SaUtr in the auth body which has the NotYetActivated state" in {

      val authResponse =
        s"""
           |{
           |    "confidenceLevel": 200,
           |    "nino": "$generatedNino",
           |    "name": {
           |        "name": "John",
           |        "lastName": "Smith"
           |    },
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
           |       },
           |       {
           |            "key":"IR-SA",
           |            "identifiers": [
           |                {
           |                    "key":"UTR",
           |                    "value": "$generatedUtr"
           |                }
           |            ],
           |            "state": "NotYetActivated"
           |        },
           |       {
           |            "key":"HMRC-MTD-IT",
           |            "identifiers": [
           |                {
           |                    "key":"MTDITID",
           |                    "value": "$generatedUtr"
           |                }
           |            ],
           |            "state": "Activated"
           |        }
           |    ],
           |    "affinityGroup": "Individual",
           |    "credentialStrength": "strong"
           |}
           |""".stripMargin

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(generatedUtr) mustBe true
      contentAsString(result).contains(Messages("label.self_assessment")) mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains(Messages("label.activate_your_self_assessment")) mustBe true
    }

    "show SaUtr and Complete your tax return message when user has an SaUtr in the auth body which has the Activated state" in {

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(saUTRActivatedAuthResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(generatedUtr) mustBe true
      contentAsString(result).contains(Messages("label.self_assessment")) mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
    }

    "return unauthorised if no MDT enrolment present" in {
      val authResponse =
        s"""
           |{
           |    "confidenceLevel": 200,
           |    "nino": "$generatedNino",
           |    "name": {
           |        "name": "John",
           |        "lastName": "Smith"
           |    },
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

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(Messages("label.self_assessment")) mustBe false

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe UNAUTHORIZED
    }

    "show the SEISS card and links when the user has SEISS data and an SA UTR in the auth body that is in the Activated state" in {

      val requestBody: String =
        s"""
           |{
           |   "utr": "$generatedUtr"
           |}
      """.stripMargin

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(saUTRActivatedAuthResponse)))
      server.stubFor(
        post(urlEqualTo("/self-employed-income-support/get-claims"))
          .withRequestBody(equalToJson(requestBody))
          .willReturn(ok(seissClaimsResponse))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(Messages("label.self_assessment")) mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains(Messages("title.seiss")) mustBe true
      contentAsString(resultSa).contains(Messages("title.seiss")) mustBe true
    }

    "not show the SEISS card and links when the user does not have SEISS data" in {

      val requestBody: String =
        s"""
           |{
           |   "utr": "$generatedUtr"
           |}
      """.stripMargin

      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(saUTRActivatedAuthResponse)))
      server.stubFor(
        post(urlEqualTo("/self-employed-income-support/get-claims"))
          .withRequestBody(equalToJson(requestBody))
          .willReturn(ok(seissClaimsEmptyResponse))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      contentAsString(result).contains(Messages("label.self_assessment")) mustBe true

      val urlSa                    = "/personal-account/self-assessment-home"
      val requestSa                = FakeRequest(GET, urlSa)
        .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
      val resultSa: Future[Result] = route(app, requestSa).get
      httpStatus(resultSa) mustBe OK
      contentAsString(resultSa).contains(Messages("title.seiss")) mustBe false
      contentAsString(resultSa).contains(Messages("title.seiss")) mustBe false
    }
  }
}
