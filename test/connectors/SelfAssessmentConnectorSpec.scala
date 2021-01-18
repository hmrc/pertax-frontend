/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import models.{NotEnrolledSelfAssessmentUser, SaEnrolmentRequest, SaEnrolmentResponse, UserDetails}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import util.UserRequestFixture.buildUserRequest
import util.{BaseSpec, WireMockHelper}

class SelfAssessmentConnectorSpec extends BaseSpec with WireMockHelper {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "external-url.add-taxes-frontend.host" -> s"http://localhost:${server.port()}"
    )
    .build()

  def sut: SelfAssessmentConnector = injected[SelfAssessmentConnector]

  val url = "/internal/self-assessment/enrol-for-sa"

  val utr: SaUtr = new SaUtrGenerator().nextSaUtr

  val providerId: String = UUID.randomUUID().toString

  val origin = "pta-sa"

  implicit val userRequest =
    buildUserRequest(
      request = FakeRequest(),
      saUser = NotEnrolledSelfAssessmentUser(utr),
      credentials = Credentials(providerId, UserDetails.GovernmentGatewayAuthProvider),
    )

  "SelfAssessmentConnector" when {

    "enrolForSelfAssessment is called" should {

      "return a redirect Url" when {

        "the correct payload is submitted (including UTR)" in {

          val redirectUrl = "/foo"

          val response =
            s"""
               |{
               |  "redirectUrl": "$redirectUrl"
               |}""".stripMargin

          server.stubFor(
            post(urlEqualTo(url)).willReturn(ok(response))
          )

          await(
            sut
              .enrolForSelfAssessment(
                SaEnrolmentRequest(origin, Some(utr), providerId)
              )) shouldBe Some(SaEnrolmentResponse(redirectUrl))
        }

        "the correct payload is submitted (excluding UTR)" in {

          val redirectUrl = "/foo"

          val response =
            s"""
               |{
               |  "redirectUrl": "$redirectUrl"
               |}""".stripMargin

          server.stubFor(
            post(urlEqualTo(url)).willReturn(ok(response))
          )

          await(
            sut
              .enrolForSelfAssessment(
                SaEnrolmentRequest(origin, None, providerId)
              )) shouldBe Some(SaEnrolmentResponse(redirectUrl))
        }
      }

      "return None" when {

        "an invalid origin is submitted" in {

          val invalidOrigin = "an_invalid_origin"

          server.stubFor(
            post(urlEqualTo(url)).willReturn(badRequest().withBody(s"Invalid origin: $invalidOrigin"))
          )

          await(
            sut
              .enrolForSelfAssessment(
                SaEnrolmentRequest(invalidOrigin, Some(utr), providerId)
              )) shouldBe None
        }

        "an invalid utr is submitted" in {

          val invalidUtr = s"&${utr.utr}"

          server.stubFor(
            post(urlEqualTo(url)).willReturn(badRequest().withBody(s"Invalid utr: $invalidUtr"))
          )

          await(
            sut
              .enrolForSelfAssessment(
                SaEnrolmentRequest(origin, Some(SaUtr(invalidUtr)), providerId)
              )) shouldBe None
        }

        "multiple invalid fields are submitted" in {

          val invalidOrigin = "an_invalid_origin"
          val invalidUtr = s"&${utr.utr}"

          server.stubFor(
            post(urlEqualTo(url))
              .willReturn(badRequest().withBody(s"Invalid origin: $invalidOrigin, Invalid utr: $invalidUtr"))
          )

          await(
            sut
              .enrolForSelfAssessment(
                SaEnrolmentRequest(invalidOrigin, Some(SaUtr(invalidUtr)), providerId)
              )) shouldBe None
        }

        "an upstream error occurs" in {

          server.stubFor(
            post(urlEqualTo(url)).willReturn(serverError())
          )

          await(
            sut
              .enrolForSelfAssessment(
                SaEnrolmentRequest(origin, Some(utr), providerId)
              )) shouldBe None
        }

        "an exception is thrown" in {

          server.stubFor(
            post(urlEqualTo(url)).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK))
          )

          await(
            sut
              .enrolForSelfAssessment(
                SaEnrolmentRequest(origin, Some(utr), providerId)
              )) shouldBe None
        }
      }
    }
  }
}
