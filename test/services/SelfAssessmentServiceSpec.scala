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

package services

import cats.data.EitherT
import connectors.SelfAssessmentConnector
import controllers.auth.requests.UserRequest
import models.{NotEnrolledSelfAssessmentUser, SaEnrolmentResponse, UserDetails}
import org.mockito.ArgumentMatchers.any
import play.api.http.Status._
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.BaseSpec
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import java.util.UUID
import scala.concurrent.Future
import org.mockito.Mockito.{times, verify, when}

class SelfAssessmentServiceSpec extends BaseSpec {

  val mockSelfAssessmentConnector: SelfAssessmentConnector = mock[SelfAssessmentConnector]

  def sut: SelfAssessmentService = new SelfAssessmentService(mockSelfAssessmentConnector, config)

  val utr: SaUtr = new SaUtrGenerator().nextSaUtr

  val providerId: String = UUID.randomUUID().toString

  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(
      request = FakeRequest(),
      saUser = NotEnrolledSelfAssessmentUser(utr),
      credentials = Credentials(providerId, UserDetails.GovernmentGatewayAuthProvider)
    )

  "SelfAssessmentService" when {

    "getSaEnrolmentUrl is called" must {

      "return a redirect Url" when {
        implicit val writes: OWrites[SaEnrolmentResponse] = Json.writes[SaEnrolmentResponse]

        "the connector returns a successful response" in {

          val redirectUrl = "/foo"

          when(mockSelfAssessmentConnector.enrolForSelfAssessment(any())(any())).thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](
              Future.successful(Right(HttpResponse(OK, Json.toJson(SaEnrolmentResponse(redirectUrl)).toString)))
            )
          )

          sut.getSaEnrolmentUrl.value.futureValue mustBe Right(Some(redirectUrl))
        }
      }

      "return UpstreamErrorResponse" when {

        List(
          BAD_REQUEST,
          NOT_FOUND,
          REQUEST_TIMEOUT,
          UNPROCESSABLE_ENTITY,
          INTERNAL_SERVER_ERROR,
          BAD_GATEWAY,
          SERVICE_UNAVAILABLE
        ).foreach { error =>
          s"the connector returns a $error response" in {

            when(mockSelfAssessmentConnector.enrolForSelfAssessment(any())(any())).thenReturn(
              EitherT[Future, UpstreamErrorResponse, HttpResponse](
                Future.successful(Left(UpstreamErrorResponse("", error)))
              )
            )

            sut.getSaEnrolmentUrl.value.futureValue.swap
              .getOrElse(UpstreamErrorResponse("", OK))
              .statusCode mustBe error
          }
        }
      }
    }
  }
}
