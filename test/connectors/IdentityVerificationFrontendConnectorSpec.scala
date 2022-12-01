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

package connectors

import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.test.{DefaultAwaitTimeout, Injecting}
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.util.Random

class IdentityVerificationFrontendConnectorSpec
    extends ConnectorSpec
    with WireMockHelper
    with MockitoSugar
    with DefaultAwaitTimeout
    with Injecting {

  override implicit lazy val app: Application = app(
    Map("microservice.services.identity-verification-frontend.port" -> server.port())
  )

  val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

  trait SpecSetup {
    val metricId = "get-iv-journey-status"

    lazy val (connector, metrics, timer) = {
      val serviceConfig                                                                = app.injector.instanceOf[ServicesConfig]
      val timer                                                                        = mock[Timer.Context]
      val identityVerificationFrontendConnector: IdentityVerificationFrontendConnector =
        new IdentityVerificationFrontendConnector(
          inject[HttpClient],
          mock[Metrics],
          serviceConfig,
          inject[HttpClientResponse]
        ) {

          override val metricsOperator: MetricsOperator = mock[MetricsOperator]
          when(metricsOperator.startTimer(any())) thenReturn timer
        }

      (identityVerificationFrontendConnector, identityVerificationFrontendConnector.metricsOperator, timer)
    }
  }

  "Calling IdentityVerificationFrontend.getIVJourneyStatus" must {

    "return an HttpResponse containing a journey status object when called with a journeyId" in new SpecSetup {
      stubGet("/mdtp/journey/journeyId/1234", OK, Some("""{"journeyResult": "LockedOut"}""".stripMargin))

      val result: HttpResponse =
        connector.getIVJourneyStatus("1234").value.futureValue.getOrElse(HttpResponse(BAD_REQUEST, ""))

      result.status mustBe OK
      (result.json \ "journeyResult").as[String] mustBe "LockedOut"
      verify(metrics, times(1)).startTimer(metricId)
      verify(metrics, times(1)).incrementSuccessCounter(metricId)
      verify(timer, times(1)).stop()
    }

    List(
      TOO_MANY_REQUESTS,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE,
      IM_A_TEAPOT,
      NOT_FOUND,
      BAD_REQUEST,
      UNPROCESSABLE_ENTITY
    ).foreach { statusCode =>
      s"return Left when a $statusCode is retreived" in new SpecSetup {
        stubGet("/mdtp/journey/journeyId/1234", statusCode, None)

        val result =
          connector.getIVJourneyStatus("1234").value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))

        result.statusCode mustBe statusCode
        verify(metrics, times(1)).startTimer(metricId)
        verify(metrics, times(1)).incrementFailedCounter(metricId)
        verify(timer, times(1)).stop()
      }
    }
  }
}
