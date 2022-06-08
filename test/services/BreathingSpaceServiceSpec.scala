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
import connectors.BreathingSpaceConnector
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.Configuration
import play.api.http.Status._
import play.api.i18n.Langs
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.{BaseSpec, Fixtures, FutureEarlyTimeout, RateLimitedException}

import scala.concurrent.Future

class BreathingSpaceServiceSpec extends BaseSpec {

  private val mockBreathingSpaceConnector: BreathingSpaceConnector =
    mock[BreathingSpaceConnector]

  def sut: BreathingSpaceService =
    new BreathingSpaceService(config, mockBreathingSpaceConnector)

  val nino: Nino = Fixtures.fakeNino

  "BreathingSpaceService getBreathingSpaceIndicator is called" must {

    "return true when Nino is Some(_) and response from connector is true" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(EitherT[Future, UpstreamErrorResponse, Boolean](Future(Right(true))))

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe true

    }

    "return false when isBreathingSpaceIndicatorEnabled is false" in {

      val stubConfigDecorator = new ConfigDecorator(
        injected[Configuration],
        injected[Langs],
        injected[ServicesConfig]
      ) {
        override lazy val isBreathingSpaceIndicatorEnabled: Boolean = false
      }

      def newSut: BreathingSpaceService =
        new BreathingSpaceService(stubConfigDecorator, mockBreathingSpaceConnector)

      newSut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false

    }

    "return false when Nino is None" in {

      sut
        .getBreathingSpaceIndicator(None)
        .futureValue mustBe false

    }

    "return false when response from connector is false" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(EitherT[Future, UpstreamErrorResponse, Boolean](Future(Right(false))))

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false

    }

    "return false when response from connector is Left NOT_FOUND response" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](
            Future(Left(UpstreamErrorResponse("NOT FOUND", NOT_FOUND, NOT_FOUND)))
          )
        )

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false
    }

    "return false when response from connector is Left INTERNAL_SERVER_ERROR response" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](
            Future(Left(UpstreamErrorResponse("INTERNAL SERVER ERROR", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))
          )
        )

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false
    }

    "return false when response from connector is Left TOO_MANY_REQUESTS response" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](
            Future(Left(UpstreamErrorResponse("TOO MANY REQUESTS", TOO_MANY_REQUESTS, TOO_MANY_REQUESTS)))
          )
        )

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false
    }

    "throws BadRequestException when BadRequestException is thrown from connector" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](Future.failed(new BadRequestException("BAD REQUEST")))
        )

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false
    }

    "throws UnauthorizedException when UnauthorizedException is thrown from connector" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](Future.failed(new UnauthorizedException("Unauthorized")))
        )

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false
    }

    "throws HttpException when HttpException is thrown from connector" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](Future.failed(new HttpException("FORBIDDEN", FORBIDDEN)))
        )

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false
    }

    "return false when FutureEarlyTimeout is thrown from connector" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](Future.failed(FutureEarlyTimeout))
        )

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false

    }

    "return false when RateLimitedException is thrown from connector" in {

      when(mockBreathingSpaceConnector.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Boolean](Future.failed(RateLimitedException))
        )

      sut
        .getBreathingSpaceIndicator(Some(nino))
        .futureValue mustBe false
    }

  }
}
