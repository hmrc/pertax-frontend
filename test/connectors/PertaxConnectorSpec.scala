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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, urlEqualTo}
import models.{ErrorView, PertaxResponse}
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import testUtils.{Fixtures, WireMockHelper}
import uk.gov.hmrc.http.UpstreamErrorResponse

class PertaxConnectorSpec extends ConnectorSpec with WireMockHelper with IntegrationPatience {

  override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.pertax.port" -> server.port()
      )
      .build()

  lazy val pertaxConnector: PertaxConnector = app.injector.instanceOf[PertaxConnector]

  def authoriseUrl(nino: String): String = s"/pertax/$nino/authorise"

  "PertaxConnector" must {
    "return a PertaxResponse with ACCESS_GRANTED code" in {
      server.stubFor(
        get(urlEqualTo(authoriseUrl(Fixtures.fakeNino.nino)))
          .willReturn(ok("{\"code\": \"ACCESS_GRANTED\", \"message\": \"Access granted\"}"))
      )

      val result = pertaxConnector
        .pertaxAuthorise(Fixtures.fakeNino.nino)
        .value
        .futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT", None, None))
      result mustBe PertaxResponse("ACCESS_GRANTED", "Access granted", None, None)
    }

    "return a PertaxResponse with NO_HMRC_PT_ENROLMENT code with a redirect link" in {
      server.stubFor(
        get(urlEqualTo(authoriseUrl(Fixtures.fakeNino.nino)))
          .willReturn(
            ok(
              "{\"code\": \"NO_HMRC_PT_ENROLMENT\", \"message\": \"There is no valid HMRC PT enrolment\", \"redirect\": \"/tax-enrolment-assignment-frontend/account\"}"
            )
          )
      )

      val result = pertaxConnector
        .pertaxAuthorise(Fixtures.fakeNino.nino)
        .value
        .futureValue
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
        get(urlEqualTo(authoriseUrl(Fixtures.fakeNino.nino)))
          .willReturn(
            ok(
              "{\"code\": \"INVALID_AFFINITY\", \"message\": \"The user is neither an individual or an organisation\", \"errorView\": {\"url\": \"/path/for/partial\", \"statusCode\": 401}}"
            )
          )
      )

      val result = pertaxConnector
        .pertaxAuthorise(Fixtures.fakeNino.nino)
        .value
        .futureValue
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
        get(urlEqualTo(authoriseUrl(Fixtures.fakeNino.nino)))
          .willReturn(
            ok(
              "{\"code\": \"MCI_RECORD\", \"message\": \"Manual correspondence indicator is set\", \"errorView\": {\"url\": \"/path/for/partial\", \"statusCode\": 423}}"
            )
          )
      )

      val result = pertaxConnector
        .pertaxAuthorise(Fixtures.fakeNino.nino)
        .value
        .futureValue
        .getOrElse(PertaxResponse("INCORRECT RESPONSE", "INCORRECT", None, None))
      result mustBe PertaxResponse(
        "MCI_RECORD",
        "Manual correspondence indicator is set",
        Some(ErrorView("/path/for/partial", 423)),
        None
      )
    }

    "return a UpstreamErrorResponse with the correct error code" when {

      List(
        BAD_REQUEST,
        NOT_FOUND,
        FORBIDDEN,
        INTERNAL_SERVER_ERROR
      ).foreach { error =>
        s"an $error is returned from the backend" in {

          server.stubFor(
            get(urlEqualTo(authoriseUrl(Fixtures.fakeNino.nino))).willReturn(
              aResponse()
                .withStatus(error)
            )
          )

          val result = pertaxConnector
            .pertaxAuthorise(Fixtures.fakeNino.nino)
            .value
            .futureValue
            .swap
            .getOrElse(UpstreamErrorResponse("INCORRECT RESPONSE", IM_A_TEAPOT))
          result.statusCode mustBe error
        }
      }
    }
  }
}
