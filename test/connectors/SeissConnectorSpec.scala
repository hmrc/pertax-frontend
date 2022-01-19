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
import com.github.tomakehurst.wiremock.http.Fault
import models.{ActivatedOnlineFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, SaEnrolmentRequest, SaEnrolmentResponse, UserDetails}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import util.UserRequestFixture.buildUserRequest
import util.{BaseSpec, WireMockHelper}

import java.util.UUID

class SeissConnectorSpec extends BaseSpec with WireMockHelper {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.add-taxes-frontend.port" -> server.port()
    )
    .build()

  def sut: SeissConnector = injected[SeissConnector]

  val url = "/get-claims"

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

    "hasClaims is called" must {

      "return true" when {

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
            .hasClaims(Some(utr.toString())) mustBe true
        }
      }
      "return false" when {
        "the user has no seiss data" in {

          val response =
            s"""
               |[
               |]""".stripMargin

          server.stubFor(
            post(urlEqualTo(url)).willReturn(ok(response))
          )

          sut
            .hasClaims(Some(utr.toString())) mustBe false

        }
      }
    }
  }
}
