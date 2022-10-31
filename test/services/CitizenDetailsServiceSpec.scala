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

package services

import cats.data.EitherT
import config.ConfigDecorator
import connectors.CitizenDetailsConnector
import models.{ETag, MatchingDetails}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.IntegrationPatience
import play.api.http.Status._
import play.api.libs.json.{Json, OWrites}
import play.api.test.Injecting
import testUtils.BaseSpec
import testUtils.Fixtures._
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import scala.concurrent.Future

class CitizenDetailsServiceSpec extends BaseSpec with Injecting with IntegrationPatience {

  val mockConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]

  val sut: CitizenDetailsService = new CitizenDetailsService(inject[ConfigDecorator], mockConnector)

  "CitizenDetailsService" when {
    "personDetails is called" must {
      "return person details when connector returns and OK status with body" in {
        when(mockConnector.personDetails(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future.successful(Right(HttpResponse(OK, Json.toJson(buildPersonDetails).toString)))
          )
        )

        val result =
          sut.personDetails(fakeNino).value.futureValue.right.getOrElse(buildPersonDetails.copy(address = None))
        result mustBe buildPersonDetails
      }

      List(
        BAD_REQUEST,
        NOT_FOUND,
        TOO_MANY_REQUESTS,
        REQUEST_TIMEOUT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        BAD_GATEWAY
      ).foreach { errorResponse =>
        s"return an UpstreamErrorResponse containing $errorResponse when connector returns the same" in {
          when(mockConnector.personDetails(any())(any(), any())).thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](
              Future.successful(Left(UpstreamErrorResponse("", errorResponse)))
            )
          )

          val result =
            sut.personDetails(fakeNino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
          result mustBe errorResponse
        }
      }
    }

    "updateAddress is called" must {
      "return HttpResponse with an OK status when connector returns and OK status with body" in {
        when(mockConnector.updateAddress(any(), any(), any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future.successful(Right(HttpResponse(OK, "")))
          )
        )

        val result =
          sut
            .updateAddress(fakeNino, etag, buildFakeAddress)
            .value
            .futureValue
            .right
            .getOrElse(HttpResponse(BAD_REQUEST, ""))
            .status
        result mustBe OK
      }

      List(
        BAD_REQUEST,
        NOT_FOUND,
        TOO_MANY_REQUESTS,
        REQUEST_TIMEOUT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        BAD_GATEWAY
      ).foreach { errorResponse =>
        s"return an UpstreamErrorResponse containing $errorResponse when connector returns the same" in {
          when(mockConnector.updateAddress(any(), any(), any())(any(), any())).thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](
              Future.successful(Left(UpstreamErrorResponse("", errorResponse)))
            )
          )

          val result =
            sut
              .updateAddress(fakeNino, etag, buildFakeAddress)
              .value
              .futureValue
              .swap
              .getOrElse(UpstreamErrorResponse("", OK))
              .statusCode
          result mustBe errorResponse
        }
      }
    }

    "getMatchingDetails is called" must {
      implicit val matchingDetailsWrites: OWrites[MatchingDetails] = Json.writes[MatchingDetails]

      "return matching details when connector returns and OK status with body" in {
        when(mockConnector.getMatchingDetails(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future.successful(
              Right(
                HttpResponse(OK, Json.obj("ids" -> Json.obj("sautr" -> saUtr.utr)).toString)
              )
            )
          )
        )

        val result =
          sut
            .getMatchingDetails(fakeNino)
            .value
            .futureValue
            .right
            .get

        result mustBe MatchingDetails(Some(saUtr))
      }

      "return error when connector returns and OK status with no body" in {
        when(mockConnector.getMatchingDetails(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future.successful(Right(HttpResponse(OK, Json.obj("ids" -> "").toString)))
          )
        )

        val result =
          sut
            .getMatchingDetails(fakeNino)
            .value
            .futureValue
            .right
            .get
            .saUtr
        result mustBe None
      }

      List(
        BAD_REQUEST,
        NOT_FOUND,
        TOO_MANY_REQUESTS,
        REQUEST_TIMEOUT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        BAD_GATEWAY
      ).foreach { errorResponse =>
        s"return an UpstreamErrorResponse containing $errorResponse when connector returns the same" in {
          when(mockConnector.getMatchingDetails(any())(any(), any())).thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](
              Future.successful(Left(UpstreamErrorResponse("", errorResponse)))
            )
          )

          val result =
            sut.getMatchingDetails(fakeNino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
          result mustBe errorResponse
        }
      }
    }

    "getEtag is called" must {
      implicit val etagWrites: OWrites[ETag] = Json.writes[ETag]

      "return etag when connector returns and OK status with body" in {
        when(mockConnector.getEtag(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future.successful(Right(HttpResponse(OK, Json.toJson(ETag("1")).toString)))
          )
        )

        val result =
          sut.getEtag(fakeNino.nino).value.futureValue.right.getOrElse(Some(ETag("wrong etag")))
        result mustBe Some(ETag("1"))
      }

      List(
        BAD_REQUEST,
        NOT_FOUND,
        TOO_MANY_REQUESTS,
        REQUEST_TIMEOUT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        BAD_GATEWAY
      ).foreach { errorResponse =>
        s"return an UpstreamErrorResponse containing $errorResponse when connector returns the same" in {
          when(mockConnector.getEtag(any())(any(), any())).thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](
              Future.successful(Left(UpstreamErrorResponse("", errorResponse)))
            )
          )

          val result =
            sut.getEtag(fakeNino.nino).value.futureValue.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode
          result mustBe errorResponse
        }
      }
    }

  }

}
