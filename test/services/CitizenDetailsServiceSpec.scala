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
import connectors.CitizenDetailsConnector
import models.admin.GetPersonFromCitizenDetailsToggle
import models.MatchingDetails
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.concurrent.IntegrationPatience
import play.api.http.Status.*
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers.GET
import play.api.test.{FakeRequest, Injecting}
import testUtils.BaseSpec
import testUtils.Fixtures.*
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class CitizenDetailsServiceSpec extends BaseSpec with Injecting with IntegrationPatience {

  val mockConnector: CitizenDetailsConnector                = mock[CitizenDetailsConnector]
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/")
  val sut: CitizenDetailsService                            = new CitizenDetailsService(mockConnector, mockFeatureFlagService)

  val etagErrorResponse              =
    "The remote endpoint has indicated that Optimistic Lock value is not correct."
  val etagErrorUpstreamErrorResponse =
    s"""POST of 'https://citizen-details.protected.mdtp:443/citizen-details/<nino>/designatory-details/address' returned 400. Response body: '{"reason":"$etagErrorResponse"}'"""

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConnector)
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))
  }

  "CitizenDetailsService" when {
    "personDetails is called" must {

      "return person details when connector returns and OK status with body" in {
        when(mockConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, JsValue](
            Future.successful(Right(Json.toJson(buildPersonDetails)))
          )
        )

        when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

        val result =
          sut.personDetails(fakeNino).value.futureValue

        result mustBe a[Right[_, _]]
        result.getOrElse(buildPersonDetails.copy(address = None)) mustBe Some(buildPersonDetails)
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

          when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
            .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

          when(mockConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
            EitherT[Future, UpstreamErrorResponse, JsValue](
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
      "return Right(true) when connector returns and OK status with body" in {
        when(mockConnector.updateAddress(any(), any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](
            Future.successful(Right(true))
          )
        )

        val result: Either[UpstreamErrorResponse, Boolean] =
          sut
            .updateAddress(fakeNino, buildFakeAddress, buildPersonDetails)
            .value
            .futureValue

        result mustBe Right(true)
      }

      "Retries with new Etag successfully when connector returns Etag error on first attempt" in {
        when(mockConnector.updateAddress(any(), any(), any())(any(), any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(
                Left(
                  UpstreamErrorResponse(
                    etagErrorUpstreamErrorResponse,
                    BAD_REQUEST
                  )
                )
              )
            ),
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(Right(true))
            )
          )

        when(mockConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, JsValue](
            Future.successful(Right(Json.toJson(buildPersonDetails.copy(etag = "newEtag"))))
          )
        )

        val result: Either[UpstreamErrorResponse, Boolean] =
          sut
            .updateAddress(fakeNino, buildFakeAddress, buildPersonDetails)
            .value
            .futureValue

        result mustBe Right(true)
        verify(mockConnector, times(2)).updateAddress(any(), any(), any())(any(), any(), any())
        verify(mockConnector, times(1)).personDetails(any(), any())(any(), any(), any())
      }

      "Do not retry with new Etag when connector returns Etag error on first attempt and address has changed" in {
        when(mockConnector.updateAddress(any(), any(), any())(any(), any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(Left(UpstreamErrorResponse(etagErrorUpstreamErrorResponse, BAD_REQUEST)))
            ),
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(Right(true))
            )
          )

        val personDetaisWithNewAddress = buildPersonDetails.copy(
          address = Some(
            buildPersonDetails.address.get.copy(line1 = Some("A different address line"))
          )
        )
        when(mockConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, JsValue](
            Future.successful(Right(Json.toJson(personDetaisWithNewAddress)))
          )
        )

        val result: Either[UpstreamErrorResponse, Boolean] =
          sut
            .updateAddress(fakeNino, buildFakeAddress, buildPersonDetails)
            .value
            .futureValue

        result mustBe a[Left[UpstreamErrorResponse, _]]
        result.swap.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe BAD_REQUEST
        verify(mockConnector, times(1)).updateAddress(any(), any(), any())(any(), any(), any())
        verify(mockConnector, times(1)).personDetails(any(), any())(any(), any(), any())
      }

      "Do not retry multiple times on Etag Error" in {
        when(mockConnector.updateAddress(any(), any(), any())(any(), any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(Left(UpstreamErrorResponse(etagErrorUpstreamErrorResponse, BAD_REQUEST)))
            )
          )

        when(mockConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, JsValue](
            Future.successful(Right(Json.toJson(buildPersonDetails.copy(etag = "newEtag"))))
          )
        )

        val result: Either[UpstreamErrorResponse, Boolean] =
          sut
            .updateAddress(fakeNino, buildFakeAddress, buildPersonDetails)
            .value
            .futureValue

        result mustBe a[Left[UpstreamErrorResponse, _]]
        result.swap.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe BAD_REQUEST
        verify(mockConnector, times(2)).updateAddress(any(), any(), any())(any(), any(), any())
        verify(mockConnector, times(1)).personDetails(any(), any())(any(), any(), any())
      }

      "Retries with new Etag successfully when connector returns Conflict on first attempt" in {
        when(mockConnector.updateAddress(any(), any(), any())(any(), any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(
                Left(
                  UpstreamErrorResponse(
                    "",
                    CONFLICT
                  )
                )
              )
            ),
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(Right(true))
            )
          )

        when(mockConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, JsValue](
            Future.successful(Right(Json.toJson(buildPersonDetails.copy(etag = "newEtag"))))
          )
        )

        val result: Either[UpstreamErrorResponse, Boolean] =
          sut
            .updateAddress(fakeNino, buildFakeAddress, buildPersonDetails)
            .value
            .futureValue

        result mustBe Right(true)
        verify(mockConnector, times(2)).updateAddress(any(), any(), any())(any(), any(), any())
        verify(mockConnector, times(1)).personDetails(any(), any())(any(), any(), any())
      }

      "Do not retry with new Etag when connector returns conflict on first attempt and address has changed" in {
        when(mockConnector.updateAddress(any(), any(), any())(any(), any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(Left(UpstreamErrorResponse("", CONFLICT)))
            ),
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(Right(true))
            )
          )

        val personDetaisWithNewAddress = buildPersonDetails.copy(
          address = Some(
            buildPersonDetails.address.get.copy(line1 = Some("A different address line"))
          )
        )
        when(mockConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, JsValue](
            Future.successful(Right(Json.toJson(personDetaisWithNewAddress)))
          )
        )

        val result: Either[UpstreamErrorResponse, Boolean] =
          sut
            .updateAddress(fakeNino, buildFakeAddress, buildPersonDetails)
            .value
            .futureValue

        result mustBe a[Left[UpstreamErrorResponse, _]]
        result.swap.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe CONFLICT
        verify(mockConnector, times(1)).updateAddress(any(), any(), any())(any(), any(), any())
        verify(mockConnector, times(1)).personDetails(any(), any())(any(), any(), any())
      }

      "Do not retry multiple times on Conflict" in {
        when(mockConnector.updateAddress(any(), any(), any())(any(), any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Boolean](
              Future.successful(Left(UpstreamErrorResponse("", CONFLICT)))
            )
          )

        when(mockConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, JsValue](
            Future.successful(Right(Json.toJson(buildPersonDetails.copy(etag = "newEtag"))))
          )
        )

        val result: Either[UpstreamErrorResponse, Boolean] =
          sut
            .updateAddress(fakeNino, buildFakeAddress, buildPersonDetails)
            .value
            .futureValue

        result mustBe a[Left[UpstreamErrorResponse, _]]
        result.swap.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe CONFLICT
        verify(mockConnector, times(2)).updateAddress(any(), any(), any())(any(), any(), any())
        verify(mockConnector, times(1)).personDetails(any(), any())(any(), any(), any())
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
          when(mockConnector.updateAddress(any(), any(), any())(any(), any(), any())).thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](
              Future.successful(Left(UpstreamErrorResponse("", errorResponse)))
            )
          )

          val result =
            sut
              .updateAddress(fakeNino, buildFakeAddress, buildPersonDetails)
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

        result mustBe a[Right[_, MatchingDetails]]
        result.getOrElse(MatchingDetails(Some(SaUtr("Invalid")))) mustBe MatchingDetails(Some(saUtr))
      }

      "return error when connector returns and OK status with no body" in {
        when(mockConnector.getMatchingDetails(any())(any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future.successful(Right(HttpResponse(OK, Json.obj("ids" -> "").toString)))
          )
        )

        val result: Either[UpstreamErrorResponse, MatchingDetails] =
          sut
            .getMatchingDetails(fakeNino)
            .value
            .futureValue

        result mustBe a[Right[_, MatchingDetails]]
        result.getOrElse(MatchingDetails(Some(SaUtr("Invalid")))) mustBe MatchingDetails(None)
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
  }

}
