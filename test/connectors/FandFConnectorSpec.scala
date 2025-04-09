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

import config.ConfigDecorator
import play.api.Application
import play.api.test.{DefaultAwaitTimeout, Injecting}
import testUtils.WireMockHelper
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.UpstreamErrorResponse
import scala.util.Random

class FandFConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout with Injecting {

  override implicit lazy val app: Application = app(
    Map("microservice.services.fandf.port" -> server.port())
  )

  val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

  val trustedHelper: TrustedHelper = TrustedHelper("principal Name", "attorneyName", "returnLink", Some(nino.nino))

  val fandfTrustedHelperResponse: String =
    s"""
       |{
       |   "principalName": "principal Name",
       |   "attorneyName": "attorneyName",
       |   "returnLinkUrl": "returnLink",
       |   "principalNino": "$nino"
       |}
       |""".stripMargin

  trait SpecSetup {

    lazy val connector: FandFConnector = {
      val configDecorator = app.injector.instanceOf[ConfigDecorator]
      new FandFConnector(
        inject[HttpClientV2],
        configDecorator,
        inject[HttpClientResponse]
      )
    }
  }

  "Calling FandFConnector.getDelegation" must {

    "return as Some(trustedHelper) when trustedHelper json returned" in new SpecSetup {
      stubGet("/delegation/get", OK, Some(fandfTrustedHelperResponse))

      val result: Option[TrustedHelper] =
        connector.getDelegation().value.futureValue.getOrElse(None)

      result mustBe Some(trustedHelper)
    }

    List(
      SERVICE_UNAVAILABLE,
      IM_A_TEAPOT,
      BAD_REQUEST
    ).foreach { statusCode =>
      s"return Left when a $statusCode is retrieved" in new SpecSetup {
        stubGet("/delegation/get", statusCode, None)

        val result: UpstreamErrorResponse =
          connector.getDelegation().value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))

        result.statusCode mustBe statusCode
      }
    }
  }
}
