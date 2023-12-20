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

import cats.data.EitherT
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Logger
import play.api.http.Status._
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http._

import scala.concurrent.Future

class HttpClientResponseSpec extends BaseSpec with WireMockHelper with ScalaFutures with IntegrationPatience {
  private val mockLogger: Logger = mock[Logger]

  private lazy val httpClientResponseUsingMockLogger: HttpClientResponse = new HttpClientResponse {
    override protected val logger: Logger = mockLogger
  }

  private val dummyContent = "error message"

  private def clientResponseLogger(
    block: Future[Either[UpstreamErrorResponse, HttpResponse]] => EitherT[Future, UpstreamErrorResponse, HttpResponse],
    infoLevel: Set[Int],
    warnLevel: Set[Int],
    errorLevelWithThrowable: Set[Int],
    errorLevelWithoutThrowable: Set[Int]
  ): Unit = {
    infoLevel.foreach { httpResponseCode =>
      s"log message: INFO level only when response code is $httpResponseCode" in {
        reset(mockLogger)
        doNothing.when(mockLogger).warn(ArgumentMatchers.any())(ArgumentMatchers.any())
        doNothing.when(mockLogger).error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())

        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future(Left(UpstreamErrorResponse(dummyContent, httpResponseCode)))
        whenReady(block(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, httpResponseCode))

          Mockito
            .verify(mockLogger, times(1))
            .info(ArgumentMatchers.eq(dummyContent))(ArgumentMatchers.any())
          Mockito
            .verify(mockLogger, times(0))
            .warn(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
          Mockito
            .verify(mockLogger, times(0))
            .error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
        }
      }
    }

    warnLevel.foreach { httpResponseCode =>
      s"log message: WARNING level only when response is $httpResponseCode" in {
        reset(mockLogger)
        doNothing.when(mockLogger).warn(ArgumentMatchers.any())(ArgumentMatchers.any())
        doNothing.when(mockLogger).error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())

        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future(Left(UpstreamErrorResponse(dummyContent, httpResponseCode)))
        whenReady(block(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, httpResponseCode))

          Mockito
            .verify(mockLogger, times(0))
            .info(ArgumentMatchers.any())(ArgumentMatchers.any())
          Mockito
            .verify(mockLogger, times(1))
            .warn(ArgumentMatchers.any())(ArgumentMatchers.any())
          Mockito
            .verify(mockLogger, times(0))
            .error(ArgumentMatchers.eq(dummyContent), ArgumentMatchers.any())(ArgumentMatchers.any())

        }
      }
    }

    errorLevelWithThrowable.foreach { httpResponseCode =>
      s"log message: ERROR level only WITH throwable when response code is $httpResponseCode" in {
        reset(mockLogger)
        doNothing.when(mockLogger).warn(ArgumentMatchers.any())(ArgumentMatchers.any())
        doNothing.when(mockLogger).error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())

        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future(Left(UpstreamErrorResponse(dummyContent, httpResponseCode)))
        whenReady(block(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, httpResponseCode))

          Mockito
            .verify(mockLogger, times(0))
            .info(ArgumentMatchers.eq(dummyContent))(ArgumentMatchers.any())
          Mockito
            .verify(mockLogger, times(0))
            .warn(ArgumentMatchers.eq(dummyContent))(ArgumentMatchers.any())
          Mockito
            .verify(mockLogger, times(1))
            .error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())

        }
      }
    }

    errorLevelWithoutThrowable.foreach { httpResponseCode =>
      s"log message: ERROR level only WITHOUT throwable when response code is $httpResponseCode" in {
        reset(mockLogger)
        doNothing.when(mockLogger).warn(ArgumentMatchers.any())(ArgumentMatchers.any())
        doNothing.when(mockLogger).error(ArgumentMatchers.any())(ArgumentMatchers.any())

        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future(Left(UpstreamErrorResponse(dummyContent, httpResponseCode)))
        whenReady(block(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, httpResponseCode))

          Mockito
            .verify(mockLogger, times(0))
            .info(ArgumentMatchers.eq(dummyContent))(ArgumentMatchers.any())
          Mockito
            .verify(mockLogger, times(0))
            .warn(ArgumentMatchers.eq(dummyContent))(ArgumentMatchers.any())
          Mockito
            .verify(mockLogger, times(1))
            .error(ArgumentMatchers.any())(ArgumentMatchers.any())

        }
      }
    }
  }

  "read" must {
    behave like clientResponseLogger(
      httpClientResponseUsingMockLogger.read,
      infoLevel = Set(NOT_FOUND),
      warnLevel = Set(LOCKED),
      errorLevelWithThrowable = Set(UNPROCESSABLE_ENTITY, UNAUTHORIZED, FORBIDDEN),
      errorLevelWithoutThrowable = Set(TOO_MANY_REQUESTS, INTERNAL_SERVER_ERROR)
    )
  }

  "readLogForbiddenAsWarning" must {
    behave like clientResponseLogger(
      httpClientResponseUsingMockLogger.readLogForbiddenAsWarning,
      infoLevel = Set(NOT_FOUND),
      warnLevel = Set(FORBIDDEN, LOCKED),
      errorLevelWithThrowable = Set(UNPROCESSABLE_ENTITY, UNAUTHORIZED),
      errorLevelWithoutThrowable = Set(TOO_MANY_REQUESTS, INTERNAL_SERVER_ERROR)
    )
  }
}
