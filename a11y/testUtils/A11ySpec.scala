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

import org.apache.pekko.Done
import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.AllFeatureFlags
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api
import play.api.cache.AsyncCacheApi
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.scalatestaccessibilitylinter.AccessibilityMatchers

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

trait A11ySpec
    extends AnyWordSpec
    with GuiceOneAppPerSuite
    with WireMockHelper
    with ScalaFutures
    with Matchers
    with AccessibilityMatchers {

  val mockCacheApi: AsyncCacheApi = new AsyncCacheApi {
    override def set(key: String, value: Any, expiration: Duration): Future[Done] = Future.successful(Done)

    override def remove(key: String): Future[Done] = Future.successful(Done)

    override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit
      evidence$1: ClassTag[A]
    ): Future[A] = orElse

    override def get[T](key: String)(implicit evidence$2: ClassTag[T]): Future[Option[T]] = Future.successful(None)

    override def removeAll(): Future[Done] = Future.successful(Done)
  }

  lazy val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(scaled(Span(15, Seconds)), scaled(Span(100, Millis)))

  val configTaxYear        = 2021
  val testTaxYear: Int     = configTaxYear - 1
  val generatedNino: Nino  = new Generator().nextNino
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

  val designatoryDetailsResponse: String =
    s"""{
       |"person":{
       |  "firstName":"John",
       |  "middleName":"",
       |  "lastName":"Smith",
       |  "initials":"JS",
       |  "title":"Dr",
       |  "honours":"Phd.",
       |  "sex":"M",
       |  "dateOfBirth":"1945-03-18",
       |  "nino":"$generatedNino"
       |  },
       |"address":{"line1":"1 Fake Street","line2":"Fake Town","line3":"Fake City","line4":"Fake Region",
       |  "postcode":"AA1 1AA",
       |  "startDate":"2015-03-15",
       |  "type":"Residential"}
       |}""".stripMargin

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        api.inject.bind[AsyncCacheApi].toInstance(mockCacheApi),
        api.inject.bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      )
      .configure(
        "microservice.services.citizen-details.port"            -> server.port(),
        "microservice.services.auth.port"                       -> server.port(),
        "microservice.services.pertax.port"                     -> server.port(),
        "microservice.services.message-frontend.port"           -> server.port(),
        "microservice.services.agent-client-authorisation.port" -> server.port(),
        "microservice.services.breathing-space-if-proxy.port"   -> server.port()
      )

  override def beforeEach(): Unit = {
    super.beforeEach()
    org.mockito.MockitoSugar.reset(mockFeatureFlagService)
    AllFeatureFlags.list.foreach { flag =>
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(flag)))
        .thenReturn(Future.successful(FeatureFlag(flag, isEnabled = false)))
    }

    server.stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(aResponse().withBody(authResponse))
    )

    server.stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$generatedNino"))
        .willReturn(ok(citizenResponse))
    )

    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))

    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details")).willReturn(ok(designatoryDetailsResponse))
    )

    server.stubFor(
      post(urlEqualTo("/pertax/authorise"))
        .willReturn(
          aResponse()
            .withBody("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}")
        )
    )
  }
}
