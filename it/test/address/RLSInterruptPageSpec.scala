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

package address

import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, status => _, urlEqualTo}
import config.{ApplicationStartUp, CryptoProvider}
import connectors.{AgentClientAuthorisationConnector, CitizenDetailsConnector, DefaultAgentClientAuthorisationConnector, DefaultCitizenDetailsConnector}
import models.admin._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status => getStatus, _}
import play.api.{Application, inject}
import testUtils.IntegrationSpec
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.time.{Clock, ZoneId}
import scala.concurrent.Future

class RLSInterruptPageSpec extends IntegrationSpec {

  val url = "/personal-account"

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.pertax.port" -> server.port()
    )
    .disable[config.HmrcModule]
    .overrides(
      inject.bind[Clock].toInstance(Clock.systemDefaultZone.withZone(ZoneId.of("Europe/London"))),
      inject.bind[ApplicationStartUp].toSelf.eagerly(),
      inject.bind[CitizenDetailsConnector].to[DefaultCitizenDetailsConnector],
      inject.bind[AgentClientAuthorisationConnector].to[DefaultAgentClientAuthorisationConnector],
      inject.bind[Encrypter with Decrypter].toProvider[CryptoProvider]
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))
  }

  "personal-account" must {
    "show rls interrupt" when {
      "RLS indicator is set for main address" in {
        val designatoryDetails =
          s"""|
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
             |    "nino" : "$generatedNino",
             |    "deceased" : false
             |  },
             |  "address" : {
             |    "line1" : "26 fake address",
             |    "line2" : "",
             |    "line3" : "fake city",
             |    "postcode" : "CT1 1RQ",
             |    "startDate": "2009-08-29",
             |    "country" : "GREAT BRITAIN",
             |    "type" : "Residential",
             |    "status": 1
             |  }
             |}
             |""".stripMargin

        server.stubFor(
          get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
            .willReturn(ok(designatoryDetails))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")

        val result = route(app, request)

        result.map(getStatus) mustBe Some(SEE_OTHER)
        result.map(redirectLocation) mustBe Some(Some("/personal-account/update-your-address"))
      }

      "RLS indicator is set for correspondence address" in {
        val designatoryDetails =
          s"""|
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
             |    "nino" : "$generatedNino",
             |    "deceased" : false
             |  },
             |  "correspondenceAddress" : {
             |    "line1" : "26 fake address",
             |    "line2" : "",
             |    "line3" : "fake city",
             |    "postcode" : "CT1 1RQ",
             |    "startDate": "2009-08-29",
             |    "country" : "GREAT BRITAIN",
             |    "type" : "Correspondence",
             |    "status": 1
             |  }
             |}
             |""".stripMargin

        server.stubFor(
          get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
            .willReturn(ok(designatoryDetails))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
        val result  = route(app, request)

        result.map(getStatus) mustBe Some(SEE_OTHER)
        result.map(redirectLocation) mustBe Some(Some("/personal-account/update-your-address"))
      }

      "RLS indicator is set for both main address and correspondence address" in {
        val designatoryDetails =
          s"""|
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
             |    "nino" : "$generatedNino",
             |    "deceased" : false
             |  },
             |  "address" : {
             |    "line1" : "26 fake address",
             |    "line2" : "",
             |    "line3" : "fake city",
             |    "postcode" : "CT1 1RQ",
             |    "startDate": "2009-08-29",
             |    "country" : "GREAT BRITAIN",
             |    "type" : "Residential",
             |    "status": 1
             |  },
             |  "correspondenceAddress" : {
             |    "line1" : "26 fake address",
             |    "line2" : "",
             |    "line3" : "fake city",
             |    "postcode" : "CT1 1RQ",
             |    "startDate": "2009-08-29",
             |    "country" : "GREAT BRITAIN",
             |    "type" : "Correspondence",
             |    "status": 1
             |  }
             |}
             |""".stripMargin

        server.stubFor(
          get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
            .willReturn(ok(designatoryDetails))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")

        val result = route(app, request)

        result.map(getStatus) mustBe Some(SEE_OTHER)
        result.map(redirectLocation) mustBe Some(Some("/personal-account/update-your-address"))
      }

    }
  }

  "not show rls interrupt" when {
    "no rls indicator" in {
      val designatoryDetails =
        s"""|
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
           |    "nino" : "$generatedNino",
           |    "deceased" : false
           |  },
           |  "address" : {
           |    "line1" : "26 fake address",
           |    "line2" : "",
           |    "line3" : "fake city",
           |    "postcode" : "CT1 1RQ",
           |    "startDate": "2009-08-29",
           |    "country" : "GREAT BRITAIN",
           |    "type" : "Residential",
           |    "status": 0
           |  },
           |  "correspondenceAddress" : {
           |     "line1" : "26 fake address",
           |     "line2" : "",
           |     "line3" : "fake city",
           |     "postcode" : "CT1 1RQ",
           |     "startDate": "2009-08-29",
           |     "country" : "GREAT BRITAIN",
           |     "type" : "Correspondence",
           |     "status": 0
           |  }
           |}
           |""".stripMargin

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(ok(designatoryDetails))
      )

      val request = FakeRequest(GET, url)
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1", SessionKeys.sessionId -> "2")
      val result  = route(app, request)

      result.map(getStatus) mustBe Some(OK)
    }

  }
}
