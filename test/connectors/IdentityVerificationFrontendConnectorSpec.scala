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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Application
import play.api.test.{DefaultAwaitTimeout, Injecting}
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import scala.concurrent.Future
import scala.util.Random

class IdentityVerificationFrontendConnectorSpec
    extends ConnectorSpec
    with WireMockHelper
    with DefaultAwaitTimeout
    with Injecting {

  private val mockHttpClientResponse: HttpClientResponse = mock[HttpClientResponse]

  private val mockHttpClient: HttpClientV2 = mock[HttpClientV2]

  private val dummyContent = "error message"

  private val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  override implicit lazy val app: Application = app(
    Map("microservice.services.identity-verification-frontend.port" -> server.port())
  )

  val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

  trait SpecSetup {
    val metricId = "get-iv-journey-status"

    lazy val connector: IdentityVerificationFrontendConnector = {
      val serviceConfig = app.injector.instanceOf[ServicesConfig]
      new IdentityVerificationFrontendConnector(
        inject[HttpClientV2],
        serviceConfig,
        inject[HttpClientResponse]
      )
    }
  }

  "Calling IdentityVerificationFrontend.getIVJourneyStatus" must {

    "return an HttpResponse containing a journey status object when called with a journeyId" in new SpecSetup {
      stubGet("/mdtp/journey/journeyId/1234", OK, Some("""{"journeyResult": "LockedOut"}""".stripMargin))

      val result: HttpResponse =
        connector.getIVJourneyStatus("1234").value.futureValue.getOrElse(HttpResponse(BAD_REQUEST, ""))

      result.status mustBe OK
      (result.json \ "journeyResult").as[String] mustBe "LockedOut"
    }

    List(
      SERVICE_UNAVAILABLE,
      IM_A_TEAPOT,
      BAD_REQUEST
    ).foreach { statusCode =>
      s"return Left when a $statusCode is retrieved" in new SpecSetup {
        stubGet("/mdtp/journey/journeyId/1234", statusCode, None)

        val result: UpstreamErrorResponse =
          connector.getIVJourneyStatus("1234").value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))

        result.statusCode mustBe statusCode
      }
    }

    List(
      TOO_MANY_REQUESTS,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      NOT_FOUND,
      UNPROCESSABLE_ENTITY
    ).foreach { httpResponse =>
      s"return $httpResponse response from HttpClientResponse service returns not found" in {

        when(mockHttpClientResponse.read(any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future(Left(UpstreamErrorResponse(dummyContent, httpResponse)))
          )
        )

        when(mockRequestBuilder.setHeader(any()))
          .thenReturn(mockRequestBuilder)

        when(mockHttpClient.get(any[URL])(any[HeaderCarrier]))
          .thenReturn(mockRequestBuilder)

        when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
          .thenReturn(Future.successful(HttpResponse(httpResponse, "")))

        def identityVerificationFrontendConnectorWithMock: IdentityVerificationFrontendConnector =
          new IdentityVerificationFrontendConnector(mockHttpClient, inject[ServicesConfig], mockHttpClientResponse)

        val result: UpstreamErrorResponse =
          identityVerificationFrontendConnectorWithMock
            .getIVJourneyStatus("1234")
            .value
            .futureValue
            .swap
            .getOrElse(UpstreamErrorResponse("", OK))

        result.statusCode mustBe httpResponse
      }
    }
  }
}
