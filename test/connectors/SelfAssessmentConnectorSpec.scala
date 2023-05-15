/*
 * Copyright 2023 HM Revenue & Customs
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

import controllers.auth.requests.UserRequest
import models.{NotEnrolledSelfAssessmentUser, SaEnrolmentRequest}
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import java.util.UUID

class SelfAssessmentConnectorSpec extends ConnectorSpec with WireMockHelper {

  val url                = "/internal/self-assessment/enrol-for-sa"
  val utr: SaUtr         = new SaUtrGenerator().nextSaUtr
  val providerId: String = UUID.randomUUID().toString
  val origin             = "pta-sa"

  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = userRequest(
    NotEnrolledSelfAssessmentUser(utr),
    providerId
  )

  override implicit lazy val app: Application = app(
    Map("microservice.services.add-taxes-frontend.port" -> server.port())
  )

  def connector: SelfAssessmentConnector = app.injector.instanceOf[SelfAssessmentConnector]

  "SelfAssessmentConnector" when {
    "enrolForSelfAssessment is called" must {
      "return a redirect link" when {
        "the correct payload is submitted (including UTR)" in {
          val redirectUrl = "/foo"

          val response =
            s"""
               |{
               |  "redirectUrl": "$redirectUrl"
               |}
              """.stripMargin

          val request: SaEnrolmentRequest = SaEnrolmentRequest(origin, Some(utr), providerId)

          stubPost(url, OK, Some(Json.toJson(request).toString()), Some(response))

          val result =
            connector.enrolForSelfAssessment(request).value.futureValue.getOrElse(HttpResponse(BAD_REQUEST, ""))
          result.status mustBe OK
          result.body mustBe response
        }

        "the correct payload is submitted (excluding UTR)" in {
          val redirectUrl = "/foo"
          val request     = SaEnrolmentRequest(origin, None, providerId)

          val response =
            s"""
               |{
               |  "redirectUrl": "$redirectUrl"
               |}
              """.stripMargin

          stubPost(url, OK, Some(Json.toJson(request).toString()), Some(response))

          val result =
            connector.enrolForSelfAssessment(request).value.futureValue.getOrElse(HttpResponse(BAD_REQUEST, ""))
          result.status mustBe OK
          result.body mustBe response
        }
      }

      "return None" when {
        "an invalid origin is submitted" in {
          val invalidOrigin = "an_invalid_origin"
          val request       = SaEnrolmentRequest(invalidOrigin, Some(utr), providerId)

          stubPost(url, BAD_REQUEST, Some(Json.toJson(request).toString()), Some(s"Invalid origin: $invalidOrigin"))

          val result =
            connector.enrolForSelfAssessment(request).value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))
          result.statusCode mustBe BAD_REQUEST
        }

        "an invalid utr is submitted" in {
          val invalidUtr                  = s"&${utr.utr}"
          val request: SaEnrolmentRequest = SaEnrolmentRequest(origin, Some(SaUtr(invalidUtr)), providerId)

          stubPost(url, BAD_REQUEST, Some(Json.toJson(request).toString()), Some(s"Invalid utr: $invalidUtr"))

          val result =
            connector.enrolForSelfAssessment(request).value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))
          result.statusCode mustBe BAD_REQUEST
        }

        "multiple invalid fields are submitted" in {
          val invalidOrigin               = "an_invalid_origin"
          val invalidUtr                  = s"&${utr.utr}"
          val request: SaEnrolmentRequest = SaEnrolmentRequest(invalidOrigin, Some(SaUtr(invalidUtr)), providerId)

          stubPost(
            url,
            BAD_REQUEST,
            Some(Json.toJson(request).toString()),
            Some(s"Invalid origin: $invalidOrigin, Invalid utr: $invalidUtr")
          )

          val result =
            connector.enrolForSelfAssessment(request).value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))
          result.statusCode mustBe BAD_REQUEST
        }

        "an upstream error occurs" in {
          val request: SaEnrolmentRequest = SaEnrolmentRequest(origin, Some(utr), providerId)

          stubPost(url, INTERNAL_SERVER_ERROR, Some(Json.toJson(request).toString()), None)

          val result =
            connector.enrolForSelfAssessment(request).value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))
          result.statusCode mustBe INTERNAL_SERVER_ERROR
        }

        "a BAD_GATEWHEN when a HttpException is thrown" in {
          val delay: Int = 5000

          val request = SaEnrolmentRequest(origin, Some(utr), providerId)

          stubWithDelay(url, OK, Some(Json.toJson(request).toString()), None, delay)

          val result =
            connector.enrolForSelfAssessment(request).value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))
          result.statusCode mustBe BAD_GATEWAY
        }
      }
    }
  }
}
