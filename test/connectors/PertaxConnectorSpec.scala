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
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, ok, post, urlEqualTo}
import config.ConfigDecorator
import models.{ErrorView, PertaxResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.IntegrationPatience
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import testUtils.WireMockHelper
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.partials.HeaderCarrierForPartialsConverter

import java.net.URL
import scala.concurrent.Future

class PertaxConnectorSpec extends ConnectorSpec with WireMockHelper with IntegrationPatience with Injecting {

  override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.pertax.port" -> server.port()
      )
      .build()

  lazy val pertaxConnector: PertaxConnector = app.injector.instanceOf[PertaxConnector]

  private val mockHttpClientResponse: HttpClientResponse = mock[HttpClientResponse]

  private val mockHttpClient: HttpClientV2 = mock[HttpClientV2]

  private val mockConfigDecorator = mock[ConfigDecorator]

  private val dummyContent = "error message"

  def postAuthoriseUrl: String = s"/pertax/authorise"

  "PertaxConnector with post" must {
    "return a PertaxResponse with ACCESS_GRANTED code" in {
      server.stubFor(
        post(urlEqualTo(postAuthoriseUrl))
          .willReturn(ok("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}"))
      )

      val result = pertaxConnector.pertaxPostAuthorise.value.futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT", None, None))
      result mustBe PertaxResponse("ACCESS_GRANTED", "Access granted", None, None)
    }

    "return a PertaxResponse with NO_HMRC_PT_ENROLMENT code with a redirect link" in {
      server.stubFor(
        post(urlEqualTo(postAuthoriseUrl))
          .willReturn(
            ok(
              "{\"code\": \"NO_HMRC_PT_ENROLMENT\", \"message\": \"There is no valid HMRC PT enrolment\", \"redirect\": \"/tax-enrolment-assignment-frontend/account\"}"
            )
          )
      )

      val result = pertaxConnector.pertaxPostAuthorise.value.futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT", None, None))
      result mustBe PertaxResponse(
        "NO_HMRC_PT_ENROLMENT",
        "There is no valid HMRC PT enrolment",
        None,
        Some("/tax-enrolment-assignment-frontend/account")
      )
    }

    "return a PertaxResponse with INVALID_AFFINITY code and an errorView" in {
      server.stubFor(
        post(urlEqualTo(postAuthoriseUrl))
          .willReturn(
            ok(
              "{\"code\": \"INVALID_AFFINITY\", \"message\": \"The user is neither an individual or an organisation\", \"errorView\": {\"url\": \"/path/for/partial\", \"statusCode\": 401}}"
            )
          )
      )

      val result = pertaxConnector.pertaxPostAuthorise.value.futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT", None, None))

      result mustBe PertaxResponse(
        "INVALID_AFFINITY",
        "The user is neither an individual or an organisation",
        Some(ErrorView("/path/for/partial", UNAUTHORIZED)),
        None
      )
    }

    "return a PertaxResponse with MCI_RECORD code and an errorView" in {
      server.stubFor(
        post(urlEqualTo(postAuthoriseUrl))
          .willReturn(
            ok(
              "{\"code\": \"MCI_RECORD\", \"message\": \"Manual correspondence indicator is set\", \"errorView\": {\"url\": \"/path/for/partial\", \"statusCode\": 423}}"
            )
          )
      )

      val result = pertaxConnector.pertaxPostAuthorise.value.futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT", None, None))
      result mustBe PertaxResponse(
        "MCI_RECORD",
        "Manual correspondence indicator is set",
        Some(ErrorView("/path/for/partial", 423)),
        None
      )
    }

    "return a PertaxResponse with DECEASED_RECORD code and an errorView" in {
      server.stubFor(
        post(urlEqualTo(postAuthoriseUrl))
          .willReturn(
            ok(
              "{\"code\": \"DECEASED_RECORD\", \"message\": \"The deceased indicator is set\", \"errorView\": {\"url\": \"/path/for/partial\", \"statusCode\": 403}}"
            )
          )
      )

      val result = pertaxConnector.pertaxPostAuthorise.value.futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT", None, None))
      result mustBe PertaxResponse(
        "DECEASED_RECORD",
        "The deceased indicator is set",
        Some(ErrorView("/path/for/partial", FORBIDDEN)),
        None
      )
    }

    "return a UpstreamErrorResponse with the correct error code" when {

      s"an 400 is returned from the backend" in {

        server.stubFor(
          post(urlEqualTo(postAuthoriseUrl)).willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
        )

        val result = pertaxConnector.pertaxPostAuthorise.value.futureValue.swap
          .getOrElse(UpstreamErrorResponse("INCORRECT RESPONSE", IM_A_TEAPOT))
        result.statusCode mustBe BAD_REQUEST
      }

      "return a UpstreamErrorResponse with the correct error code" when {

        List(
          UNAUTHORIZED,
          NOT_FOUND,
          FORBIDDEN,
          INTERNAL_SERVER_ERROR
        ).foreach { error =>
          s"an $error is returned from the HttpClientResponse" in {

            val mockRequestBuilder = mock[RequestBuilder]
            when(mockHttpClient.post(any[URL])(any[HeaderCarrier])).thenReturn(mockRequestBuilder)

            when(mockRequestBuilder.setHeader(any[(String, String)])).thenReturn(mockRequestBuilder)
            when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
              .thenReturn(Future.successful(Left(UpstreamErrorResponse(dummyContent, error))))

            when(mockHttpClientResponse.readLogUnauthorisedAsInfo(any())).thenReturn(
              EitherT[Future, UpstreamErrorResponse, HttpResponse](
                Future(Left(UpstreamErrorResponse(dummyContent, error)))
              )
            )

            when(mockConfigDecorator.pertaxUrl).thenReturn("http://localhost:8080")

            def pertaxConnectorWithMock: PertaxConnector =
              new PertaxConnector(
                mockHttpClient,
                mockHttpClientResponse,
                mockConfigDecorator,
                inject[HeaderCarrierForPartialsConverter]
              )

            val result = pertaxConnectorWithMock.pertaxPostAuthorise.value.futureValue.swap
              .getOrElse(UpstreamErrorResponse("INCORRECT RESPONSE", IM_A_TEAPOT))
            result.statusCode mustBe error
          }
        }
      }

    }
  }
}
