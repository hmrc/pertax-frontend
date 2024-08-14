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
import org.scalatest.RecoverMethods
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Logger
import play.api.http.Status._
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http._

import scala.concurrent.Future

class HttpClientResponseSpec
    extends BaseSpec
    with WireMockHelper
    with ScalaFutures
    with IntegrationPatience
    with RecoverMethods {
  private val mockLogger: Logger = mock[Logger]

  private lazy val httpClientResponseUsingMockLogger: HttpClientResponse = new HttpClientResponse {
    override protected val logger: Logger = mockLogger
  }

  private val dummyContent = "error message"

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

  "readLogUnauthorisedAsInfo" must {
    behave like clientResponseLogger(
      httpClientResponseUsingMockLogger.readLogUnauthorisedAsInfo,
      infoLevel = Set(UNAUTHORIZED),
      warnLevel = Set(),
      errorLevelWithThrowable = Set(UNPROCESSABLE_ENTITY),
      errorLevelWithoutThrowable = Set(TOO_MANY_REQUESTS, INTERNAL_SERVER_ERROR)
    )
  }

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
        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future(Left(UpstreamErrorResponse(dummyContent, httpResponseCode)))
        whenReady(block(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, httpResponseCode))
          verifyCalls(info = Some(dummyContent))
        }
      }
    }
    warnLevel.foreach { httpResponseCode =>
      s"log message: WARNING level only when response is $httpResponseCode" in {
        reset(mockLogger)
        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future(Left(UpstreamErrorResponse(dummyContent, httpResponseCode)))
        whenReady(block(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, httpResponseCode))
          verifyCalls(warn = Some(dummyContent))
        }
      }
    }
    errorLevelWithThrowable.foreach { httpResponseCode =>
      s"log message: ERROR level only WITH throwable when response code is $httpResponseCode" in {
        reset(mockLogger)
        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future(Left(UpstreamErrorResponse(dummyContent, httpResponseCode)))
        whenReady(block(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, httpResponseCode))
          verifyCalls(errorWithThrowable = Some(dummyContent))
        }
      }
    }
    errorLevelWithoutThrowable.foreach { httpResponseCode =>
      s"log message: ERROR level only WITHOUT throwable when response code is $httpResponseCode" in {
        reset(mockLogger)
        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future(Left(UpstreamErrorResponse(dummyContent, httpResponseCode)))
        whenReady(block(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, httpResponseCode))
          verifyCalls(errorWithoutThrowable = Some(dummyContent))
        }
      }
    }
    "log message: ERROR level only WITHOUT throwable when future failed with HttpException & " +
      "recover to BAD GATEWAY" in {
        reset(mockLogger)
        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future.failed(new HttpException(dummyContent, GATEWAY_TIMEOUT))
        whenReady(block(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, BAD_GATEWAY))
          verifyCalls(errorWithoutThrowable = Some(dummyContent))
        }
      }
    "log nothing at all when future failed with non-HTTPException" in {
      reset(mockLogger)
      val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
        Future.failed(new RuntimeException(dummyContent))

      recoverToSucceededIf[RuntimeException] {
        block(response).value
      }
      verifyCalls()
    }
  }

  private def verifyCalls(
    info: Option[String] = None,
    warn: Option[String] = None,
    errorWithThrowable: Option[String] = None,
    errorWithoutThrowable: Option[String] = None
  ): Unit = {

    val infoTimes                  = info.map(_ => 1).getOrElse(0)
    val warnTimes                  = warn.map(_ => 1).getOrElse(0)
    val errorWithThrowableTimes    = errorWithThrowable.map(_ => 1).getOrElse(0)
    val errorWithoutThrowableTimes = errorWithoutThrowable.map(_ => 1).getOrElse(0)

    def argumentMatcher(content: Option[String]): String = content match {
      case None    => ArgumentMatchers.any()
      case Some(c) => ArgumentMatchers.eq(c)
    }

    Mockito
      .verify(mockLogger, times(infoTimes))
      .info(argumentMatcher(info))(ArgumentMatchers.any())
    Mockito
      .verify(mockLogger, times(warnTimes))
      .warn(argumentMatcher(warn))(ArgumentMatchers.any())
    Mockito
      .verify(mockLogger, times(errorWithThrowableTimes))
      .error(argumentMatcher(errorWithThrowable), ArgumentMatchers.any())(ArgumentMatchers.any())
    Mockito
      .verify(mockLogger, times(errorWithoutThrowableTimes))
      .error(argumentMatcher(errorWithoutThrowable))(ArgumentMatchers.any())
  }

}
