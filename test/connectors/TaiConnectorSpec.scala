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
import play.api.libs.json.{JsValue, Json}
import play.api.test.{DefaultAwaitTimeout, Injecting}
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.time.TaxYear

import scala.util.Random

class TaiConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout with Injecting {

  override implicit lazy val app: Application = app(
    Map("microservice.services.tai.port" -> server.port())
  )

  trait SpecSetup {
    val taxComponentsJson: JsValue = Json.parse("""{
        |   "data" : [ {
        |      "componentType" : "EmployerProvidedServices",
        |      "employmentId" : 12,
        |      "amount" : 12321,
        |      "description" : "Some Description",
        |      "iabdCategory" : "Benefit"
        |   }, {
        |      "componentType" : "PersonalPensionPayments",
        |      "employmentId" : 31,
        |      "amount" : 12345,
        |      "description" : "Some Description Some",
        |      "iabdCategory" : "Allowance"
        |   } ],
        |   "links" : [ ]
        |}""".stripMargin)

    lazy val connector: TaiConnector = {

      val serviceConfig = inject[ServicesConfig]
      val httpClient    = inject[HttpClientV2]

      new TaiConnector(httpClient, serviceConfig, inject[HttpClientResponse], inject[ConfigDecorator])
    }
  }

  "Calling TaiService.taxSummary" must {
    trait LocalSetup extends SpecSetup

    val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

    val taxYear = TaxYear.now().getYear

    val url = s"/tai/$nino/tax-account/$taxYear/tax-components"

    "return OK on success" in new LocalSetup {
      stubGet(url, OK, Some(taxComponentsJson.toString))

      val result: HttpResponse =
        connector.taxComponents(nino, taxYear).value.futureValue.getOrElse(HttpResponse(BAD_REQUEST, ""))
      result.status mustBe OK
      result.json mustBe taxComponentsJson
    }

    "return __ on success" in new LocalSetup {
      stubGet(url, OK, Some(taxComponentsJson.toString))

      val result: HttpResponse =
        connector.taxComponents(nino, taxYear).value.futureValue.getOrElse(HttpResponse(BAD_REQUEST, ""))
      result.status mustBe OK
      result.json mustBe taxComponentsJson
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
      s"return an UpstreamErrorResponse containing $statusCode if the same response is retrieved" in new LocalSetup {
        stubGet(url, statusCode, None)

        val result: UpstreamErrorResponse =
          connector.taxComponents(nino, taxYear).value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))
        result.statusCode mustBe statusCode
      }
    }
  }
}

class TaiConnectorTimeoutSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout with Injecting {

  override implicit lazy val app: Application = app(
    Map(
      "microservice.services.tai.port"                  -> server.port(),
      "microservice.services.tai.timeoutInMilliseconds" -> 1
    )
  )

  trait SpecSetup {
    lazy val connector: TaiConnector = {
      val serviceConfig = inject[ServicesConfig]
      val httpClient    = inject[HttpClientV2]

      new TaiConnector(httpClient, serviceConfig, inject[HttpClientResponse], inject[ConfigDecorator])
    }
  }

  "Calling TaiService.taxSummary" must {
    trait LocalSetup extends SpecSetup

    val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

    val taxYear = TaxYear.now().getYear

    val url = s"/tai/$nino/tax-account/$taxYear/tax-components"

    "return bad gateway when the call results in a timeout" in new LocalSetup {
      stubWithDelay(url, OK, None, None, 100)

      val result: UpstreamErrorResponse =
        connector.taxComponents(nino, taxYear).value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))
      result.statusCode mustBe BAD_GATEWAY
    }
  }
}
