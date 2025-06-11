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

package testUtils

import cats.data.EitherT
import com.github.tomakehurst.wiremock.client.WireMock
import org.apache.pekko.Done
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import models.admin._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api
import play.api.cache.AsyncCacheApi
import play.api.http.Status.NOT_FOUND
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.JourneyCacheRepository
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import org.mockito.Mockito.{reset => resetMock}

import java.time.LocalDateTime
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait IntegrationSpec
    extends AnyWordSpec
    with GuiceOneAppPerSuite
    with WireMockHelper
    with ScalaFutures
    with Matchers
    with IntegrationPatience {

  val mockCacheApi: AsyncCacheApi                = new AsyncCacheApi {
    override def set(key: String, value: Any, expiration: Duration): Future[Done] = Future.successful(Done)

    override def remove(key: String): Future[Done] = Future.successful(Done)

    override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit
      evidence$1: ClassTag[A]
    ): Future[A] = orElse

    override def get[T](key: String)(implicit evidence$2: ClassTag[T]): Future[Option[T]] = Future.successful(None)

    override def removeAll(): Future[Done] = Future.successful(Done)
  }
  val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  lazy val messagesApi: MessagesApi    = app.injector.instanceOf[MessagesApi]
  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val generatedNino: Nino  = new Generator().nextNino
  val utr: String          = "utr"
  val generatedUtr: String = new Generator().nextAtedUtr.utr

  val authResponse: String =
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

  val citizenResponse: String =
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
        |    "nino": "$generatedNino"
        |  },
        |  "dateOfBirth": "11121971"
        |}
        |""".stripMargin

  def personDetailsResponse(nino: String): String =
    s"""
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
       |    "nino" : "$nino",
       |    "deceased" : false
       |  },
       |  "address" : {
       |    "line1" : "Fake address",
       |    "line2" : "PO BOX 00",
       |    "line3" : "City",
       |    "postcode" : "post code",
       |    "startDate": "2009-08-29",
       |    "country" : "GREAT BRITAIN",
       |    "type" : "Residential"
       |  }
       |}
       |""".stripMargin

  val saUTRActivatedAuthResponse: String =
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
       |            "state": "Activated"
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

  val seissClaimsResponse: String =
    s"""
       |[
       |    {
       |        "_id": 1135371,
       |        "utr": "1234567890",
       |        "paymentReference": "SESE1135371",
       |        "barsRequestId": 1014,
       |        "claimant": {
       |            "name": "Foo Bar",
       |            "phoneNumber": "0772344600",
       |            "email": "foo1@example.com",
       |            "address": {
       |                "line1": "2 Other Place",
       |                "line2": "Some District",
       |                "town": "Anytown",
       |                "postCode": "ZZ11ZZ"
       |            }
       |        },
       |        "bankDetails": {
       |            "accountName": "Alex Askew",
       |            "sortCode": "206705",
       |            "accountNumber": "44344611"
       |        },
       |        "claimAmount": 45000,
       |        "status": "pendingBarsCheck",
       |        "riskingResponse": {
       |            "action": "ACCEPT"
       |        },
       |        "applicationSubmissionDate": "2021-10-26T15:07:40.439",
       |        "claimPhase": 5,
       |        "paymentEvents": [],
       |        "financialImpactInfo": {
       |            "subjectTurnover": 1200,
       |            "comparisonTurnover": 1200,
       |            "comparisonYear": 2020,
       |            "multiplier": 0.3
       |        }
       |    }
       |]
            """.stripMargin

  val seissClaimsEmptyResponse: String =
    s"""
       |[
       |]
    """.stripMargin

  val fandfTrustedHelperResponse: String =
    s"""
      |{
      |   "principalName": "principal Name",
      |   "attorneyName": "attorneyName",
      |   "returnLinkUrl": "returnLink",
      |   "principalNino": "$generatedNino"
      |}
      |""".stripMargin

  val singleAccountWrapperDataResponse: String =
    """
      |{
      |    "menuItemConfig": [
      |        {
      |            "id": "home",
      |            "text": "Account home",
      |            "href": "http://localhost:9232/personal-account",
      |            "leftAligned": true,
      |            "position": 0,
      |            "icon": "hmrc-account-icon hmrc-account-icon--home"
      |        }
      |    ],
      |    "ptaMinMenuConfig": {
      |        "menuName": "Account menu",
      |        "backName": "Back"
      |    },
      |    "urBanners": [
      |        {
      |           "page": "test-page",
      |           "link": "test-link",
      |           "isEnabled": true
      |        }
      |    ],
      |    "webchatPages": [
      |        {
      |            "pattern": "^/personal-account",
      |            "skinElement": "skinElement",
      |            "isEnabled": true
      |        }
      |    ]
      |}
      |""".stripMargin

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        api.inject.bind[AsyncCacheApi].toInstance(mockCacheApi),
        api.inject.bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      )
      .configure(
        "microservice.services.citizen-details.port"                    -> server.port(),
        "microservice.services.auth.port"                               -> server.port(),
        "microservice.services.pertax.port"                             -> server.port(),
        "microservice.services.message-frontend.port"                   -> server.port(),
        "microservice.services.agent-client-relationships.port"         -> server.port(),
        "microservice.services.breathing-space-if-proxy.port"           -> server.port(),
        "microservice.services.taxcalc-frontend.port"                   -> server.port(),
        "microservice.services.fandf.port"                              -> server.port(),
        "sca-wrapper.services.single-customer-account-wrapper-data.url" -> s"http://localhost:${server.port()}"
      )

  override def beforeEach(): Unit = {
    super.beforeEach()
    resetMock(mockFeatureFlagService)
    AllFeatureFlags.list.foreach { flag =>
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(flag)))
        .thenReturn(Future.successful(FeatureFlag(flag, isEnabled = false)))
      when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(flag)))
        .thenReturn(EitherT.rightT(FeatureFlag(flag, isEnabled = false)))
    }

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
      .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
      .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))

    server.stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(aResponse().withBody(authResponse))
    )

    server.stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$generatedNino"))
        .willReturn(ok(citizenResponse))
    )

    server.stubFor(
      get(urlMatching("/messages/count.*"))
        .willReturn(ok("{}"))
    )

    server.stubFor(
      post(urlEqualTo("/pertax/authorise"))
        .willReturn(
          aResponse()
            .withBody("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}")
        )
    )
    server.stubFor(
      WireMock
        .get(urlMatching("/delegation/get"))
        .willReturn(notFound())
    )
    server.stubFor(
      WireMock
        .get(urlEqualTo("/single-customer-account-wrapper-data/wrapper-data?lang=en&version=1.0.3"))
        .willReturn(
          aResponse()
            .withBody(singleAccountWrapperDataResponse)
        )
    )
  }

  def beforeEachHomeController(
    auth: Boolean = true,
    memorandum: Boolean = true,
    matchingDetails: Boolean = true
  ): StubMapping = {
    if (auth) {
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    }
    if (memorandum) {
      server.stubFor(get(urlMatching(s"/$generatedNino/memorandum")).willReturn(serverError()))
    }
    if (matchingDetails) {
      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    }
    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(aResponse().withStatus(NOT_FOUND))
    )
    server.stubFor(
      get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
        .willReturn(serverError())
    )
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
  }
}
