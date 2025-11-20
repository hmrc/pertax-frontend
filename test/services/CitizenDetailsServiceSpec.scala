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

        result mustBe Right(Some(buildPersonDetails))
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

      "return None when is GetPersonFromCitizenDetailsToggle disabled" in {
        when(mockConnector.personDetails(any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, JsValue](
            Future.successful(Right(Json.toJson(buildPersonDetails)))
          )
        )

        when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = false)))

        val result =
          sut.personDetails(fakeNino).value.futureValue

        result mustBe Right(None)
        verify(mockConnector, times(0)).getMatchingDetails(any())(any(), any())
      }
    }

    "updateAddress is called" must {
      "return Right(true) when connector returns and OK status with body" in {
        when(mockConnector.updateAddress(any(), any(), any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](
            Future.successful(Right(true))
          )
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

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
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))
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
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

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

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

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

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

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

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

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

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

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
      "return SaUtr when matching returns a SaUtr" in {
        when(mockConnector.getMatchingDetails(any())(any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Json.obj("ids" -> Json.obj("sautr" -> saUtr.utr)))
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

        val result =
          sut
            .getSaUtrFromMatchingDetails(fakeNino)
            .value
            .futureValue

        result mustBe Right(Some(saUtr))
      }

      "return None when connector returns a Json with no body" in {
        when(mockConnector.getMatchingDetails(any())(any(), any())).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](Json.obj("ids" -> ""))
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

        val result: Either[UpstreamErrorResponse, Option[SaUtr]] =
          sut
            .getSaUtrFromMatchingDetails(fakeNino)
            .value
            .futureValue

        result mustBe Right(None)
      }

      "return Left when connector returns a Left" in {
        when(mockConnector.getMatchingDetails(any())(any(), any())).thenReturn(
          EitherT.leftT[Future, JsValue](UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR))
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

        val result: Either[UpstreamErrorResponse, Option[SaUtr]] =
          sut
            .getSaUtrFromMatchingDetails(fakeNino)
            .value
            .futureValue

        result mustBe a[Left[UpstreamErrorResponse, _]]
      }

      "return None when GetPersonFromCitizenDetailsToggle is disabled" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
          .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = false)))

        val result: Either[UpstreamErrorResponse, Option[SaUtr]] =
          sut
            .getSaUtrFromMatchingDetails(fakeNino)
            .value
            .futureValue

        result mustBe Right(None)

        verify(mockConnector, times(0)).getMatchingDetails(any())(any(), any())

      }
    }
  }

}
