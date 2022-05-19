/*
 * Copyright 2022 HM Revenue & Customs
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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import models.{ActivatedOnlineFilerSelfAssessmentUser, SeissModel, UserDetails}
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.await
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse
import util.UserRequestFixture.buildUserRequest
import util.{BaseSpec, WireMockHelper}
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.libs.json.JsResultException

import java.util.UUID

class SeissConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.self-employed-income-support.port" -> server.port()
    )
    .build()

  def sut: SeissConnector = injected[SeissConnector]

  val url = "/self-employed-income-support/get-claims"

  val utr: SaUtr = new SaUtrGenerator().nextSaUtr

  val providerId: String = UUID.randomUUID().toString

  val origin = "pta-sa"

  implicit val userRequest =
    buildUserRequest(
      request = FakeRequest(),
      saUser = ActivatedOnlineFilerSelfAssessmentUser(utr),
      credentials = Credentials(providerId, UserDetails.GovernmentGatewayAuthProvider)
    )

  "SeissConnector" when {

    "getClaims is called" must {

      "return list of claims" when {

        "the user has seiss data" in {

          val response =
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
               |]""".stripMargin

          server.stubFor(
            post(urlEqualTo(url)).willReturn(ok(response))
          )

          sut
            .getClaims(utr.toString())
            .value
            .futureValue mustBe Right(List(SeissModel("1234567890")))
        }
      }
      "return empty list" when {
        "the user has no seiss data" in {

          val response =
            s"""
               |[
               |]""".stripMargin

          server.stubFor(
            post(urlEqualTo(url)).willReturn(ok(response))
          )

          sut
            .getClaims(utr.toString())
            .value
            .futureValue mustBe Right(List())

        }
      }

      "return Left(UpstreamErrorResponse)" when {
        List(BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND).foreach(statusCode =>
          s"a status $statusCode is returned" in {
            server.stubFor(
              post(urlEqualTo(url)).willReturn(aResponse().withStatus(statusCode))
            )

            sut
              .getClaims(utr.toString())
              .value
              .futureValue
              .left
              .get mustBe a[UpstreamErrorResponse]

          }
        )
      }
    }

    "return Left(UpstreamErrorResponse)" when {
      "there is a timeout" in {
        server.stubFor(
          post(urlEqualTo(url)).willReturn(ok("{}").withFixedDelay(5000))
        )

        sut
          .getClaims(utr.toString())
          .value
          .futureValue
          .left
          .get mustBe a[UpstreamErrorResponse]
      }
    }

    "return an exception" when {
      "json is invalid" in {
        server.stubFor(
          post(urlEqualTo(url)).willReturn(ok("""{"invalid":"invalid"}"""))
        )

        a[JsResultException] mustBe thrownBy(
          await(sut.getClaims(utr.toString()).value)
        )
      }
    }
  }
}
