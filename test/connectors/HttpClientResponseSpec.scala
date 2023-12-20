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

  "readLogForbiddenAsWarning" must {
    Set(NOT_FOUND, UNPROCESSABLE_ENTITY, UNAUTHORIZED).foreach { httpResponseCode =>
      s"log message: 0 warning & 1 error level when response code is $httpResponseCode" in {
        reset(mockLogger)
        doNothing.when(mockLogger).warn(ArgumentMatchers.any())(ArgumentMatchers.any())
        doNothing.when(mockLogger).error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())

        val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
          Future(Left(UpstreamErrorResponse(dummyContent, httpResponseCode)))
        whenReady(httpClientResponseUsingMockLogger.readLogForbiddenAsWarning(response).value) { actual =>
          actual mustBe Left(UpstreamErrorResponse(dummyContent, httpResponseCode))

          Mockito
            .verify(mockLogger, times(0))
            .warn(ArgumentMatchers.eq(dummyContent))(ArgumentMatchers.any())
          Mockito
            .verify(mockLogger, times(1))
            .error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())

        }
      }
    }
    "log message: 1 warning & 0 error level when response is FORBIDDEN" in {
      reset(mockLogger)
      doNothing.when(mockLogger).warn(ArgumentMatchers.any())(ArgumentMatchers.any())
      doNothing.when(mockLogger).error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())

      val response: Future[Either[UpstreamErrorResponse, HttpResponse]] =
        Future(Left(UpstreamErrorResponse(dummyContent, FORBIDDEN)))
      whenReady(httpClientResponseUsingMockLogger.readLogForbiddenAsWarning(response).value) { actual =>
        actual mustBe Left(UpstreamErrorResponse(dummyContent, FORBIDDEN))

        Mockito
          .verify(mockLogger, times(1))
          .warn(ArgumentMatchers.any())(ArgumentMatchers.any())
        Mockito
          .verify(mockLogger, times(0))
          .error(ArgumentMatchers.eq(dummyContent), ArgumentMatchers.any())(ArgumentMatchers.any())

      }
    }
  }
}
