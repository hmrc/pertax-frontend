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
import connectors.IdentityVerificationFrontendConnector
import org.mockito.ArgumentMatchers.any
import play.api.http.Status._
import play.api.libs.json.Json
import testUtils.BaseSpec
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import scala.concurrent.Future
import org.mockito.Mockito.{times, verify, when}

class IdentityVerificationFrontendServiceSpec extends BaseSpec {

  val identityVerificationConnector: IdentityVerificationFrontendConnector = mock[IdentityVerificationFrontendConnector]

  val identityVerificationService: IdentityVerificationFrontendService = new IdentityVerificationFrontendService(
    identityVerificationConnector
  )

  "IdentityVerificationFrontendService" when {
    "getIVJourneyStatus is called" must {
      List(
        Success,
        Incomplete,
        FailedMatching,
        InsufficientEvidence,
        LockedOut,
        UserAborted,
        Timeout,
        TechnicalIssue,
        PrecondFailed,
        InvalidResponse
      ).foreach { identityVerificationResponse =>
        s"$identityVerificationResponse if returned by the connector within its json response" in {
          when(identityVerificationConnector.getIVJourneyStatus(any())(any(), any()))
            .thenReturn(
              EitherT[Future, UpstreamErrorResponse, HttpResponse](
                Future.successful(
                  Right(
                    HttpResponse(OK, Json.obj("journeyResult" -> identityVerificationResponse.toString).toString)
                  )
                )
              )
            )

          val alt = if (identityVerificationResponse == InvalidResponse) Success else InvalidResponse

          val result = identityVerificationService
            .getIVJourneyStatus("1234")
            .value
            .futureValue
            .getOrElse(alt)

          result mustBe identityVerificationResponse
        }
      }

      List(
        TOO_MANY_REQUESTS,
        INTERNAL_SERVER_ERROR,
        BAD_GATEWAY,
        SERVICE_UNAVAILABLE,
        IM_A_TEAPOT,
        NOT_FOUND,
        BAD_REQUEST,
        UNPROCESSABLE_ENTITY
      ).foreach { statusCode =>
        s"return Left when a $statusCode is retreived" in {
          when(identityVerificationConnector.getIVJourneyStatus(any())(any(), any()))
            .thenReturn(
              EitherT[Future, UpstreamErrorResponse, HttpResponse](
                Future.successful(
                  Left(
                    UpstreamErrorResponse("", statusCode)
                  )
                )
              )
            )

          val result = identityVerificationService
            .getIVJourneyStatus("1234")
            .value
            .futureValue
            .swap
            .getOrElse(UpstreamErrorResponse("", OK))
            .statusCode
          result mustBe statusCode
        }
      }
    }
  }
}
