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
import connectors.BreathingSpaceConnector
import models.BreathingSpaceIndicatorResponse
import models.admin.BreathingSpaceIndicatorToggle
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.api.http.Status._
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import util.FutureEarlyTimeout

import scala.concurrent.Future
import org.mockito.Mockito.{times, verify, when}

class BreathingSpaceServiceSpec extends BaseSpec {

  private val mockBreathingSpaceConnector: BreathingSpaceConnector =
    mock[BreathingSpaceConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle))) thenReturn Future.successful(
      FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)
    )
  }

  val nino: Nino = Fixtures.fakeNino

  "BreathingSpaceService getBreathingSpaceIndicator is called" must {

    "return BreathingSpaceIndicatorResponse.WithinPeriod when Nino is Some(_) and response from connector is true" in {
      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(EitherT[Future, UpstreamErrorResponse, Boolean](Future(Right(true))))

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.WithinPeriod
    }

    "return BreathingSpaceIndicatorResponse.StatusUnknown when isBreathingSpaceIndicatorEnabled is false" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle))) thenReturn Future.successful(
        FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = false)
      )

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.StatusUnknown
    }

    "return BreathingSpaceIndicatorResponse.OutOfPeriod when response from connector is false" in {
      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(EitherT[Future, UpstreamErrorResponse, Boolean](Future(Right(false))))

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.OutOfPeriod
    }

    "return BreathingSpaceIndicatorResponse.NotFound when response from connector is Left NOT_FOUND response" in {
      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](
            Future(Left(UpstreamErrorResponse("NOT FOUND", NOT_FOUND, NOT_FOUND)))
          )
        )

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.NotFound
    }

    "return BreathingSpaceIndicatorResponse.StatusUnknown when response from connector is Left INTERNAL_SERVER_ERROR response" in {
      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](
            Future(Left(UpstreamErrorResponse("INTERNAL SERVER ERROR", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))
          )
        )

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.StatusUnknown
    }

    "return BreathingSpaceIndicatorResponse.StatusUnknown when response from connector is Left TOO_MANY_REQUESTS response" in {
      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](
            Future(Left(UpstreamErrorResponse("TOO MANY REQUESTS", TOO_MANY_REQUESTS, TOO_MANY_REQUESTS)))
          )
        )

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.StatusUnknown
    }

    "throws BadRequestException when BadRequestException is thrown from connector" in {
      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](Future.failed(new BadRequestException("BAD REQUEST")))
        )

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.StatusUnknown
    }

    "throws UnauthorizedException when UnauthorizedException is thrown from connector" in {
      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](Future.failed(new UnauthorizedException("Unauthorized")))
        )

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.StatusUnknown
    }

    "throws HttpException when HttpException is thrown from connector" in {
      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](Future.failed(new HttpException("FORBIDDEN", FORBIDDEN)))
        )

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.StatusUnknown
    }

    "return false when FutureEarlyTimeout is thrown from connector" in {
      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](Future.failed(FutureEarlyTimeout))
        )

      val sut: BreathingSpaceService =
        new BreathingSpaceService(mockBreathingSpaceConnector, mockFeatureFlagService)

      sut
        .getBreathingSpaceIndicator(nino)
        .futureValue mustBe BreathingSpaceIndicatorResponse.StatusUnknown
    }
  }
}
