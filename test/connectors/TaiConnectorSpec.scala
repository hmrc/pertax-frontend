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
import config.ConfigDecorator
import models.TaxComponents._
import models.admin.TaxComponentsRetrievalToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.libs.json.{JsValue, Json}
import play.api.test.{DefaultAwaitTimeout, Injecting}
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.time.TaxYear

import scala.util.Random
import scala.concurrent.Future

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
        |      "componentType" : "HICBCPaye",
        |      "employmentId" : 31,
        |      "amount" : 12345,
        |      "description" : "Some Description Some",
        |      "iabdCategory" : "Allowance"
        |   } ],
        |   "links" : [ ]
        |}""".stripMargin)

    val taxComponentsList: List[String] = List("EmployerProvidedServices", "HICBCPaye")

    lazy val connector: TaiConnector = {

      val serviceConfig = inject[ServicesConfig]
      val httpClient    = inject[HttpClientV2]

      new TaiConnector(
        httpClient,
        serviceConfig,
        inject[HttpClientResponse],
        inject[ConfigDecorator],
        mockFeatureFlagService
      )
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
      .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true)))
  }

  "Calling TaiService.taxSummary" must {
    trait LocalSetup extends SpecSetup

    val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

    val taxYear = TaxYear.now().getYear

    val url = s"/tai/$nino/tax-account/$taxYear/tax-components"

    "return OK on success when reading as a list of strings" in new LocalSetup {
      stubGet(url, OK, Some(taxComponentsJson.toString))
      val result: Option[List[String]] =
        connector
          .taxComponents(nino, taxYear)(readsListString)
          .value
          .futureValue
          .getOrElse(Some(List.empty))
      result mustBe Some(taxComponentsList)
    }

    "return None when reading invalid json" in new LocalSetup {
      stubGet(url, OK, Some("invalid"))
      val result: Option[List[String]] =
        connector
          .taxComponents(nino, taxYear)(readsListString)
          .value
          .futureValue
          .getOrElse(Some(List.empty))
      result mustBe None
    }

    "return OK on success when reading as a boolean (for HICBC)" in new LocalSetup {
      stubGet(url, OK, Some(taxComponentsJson.toString))
      val result: Option[Boolean] =
        connector
          .taxComponents(nino, taxYear)(readsIsHICBCWithCharge)
          .value
          .futureValue
          .getOrElse(None)
      result mustBe Some(true)
    }

    "return None when tax components feature toggle switched off" in new LocalSetup {
      when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
        .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = false)))

      val result: Option[List[String]] =
        connector
          .taxComponents(nino, taxYear)(readsListString)
          .value
          .futureValue
          .getOrElse(Some(List.empty))
      result mustBe None
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
          connector
            .taxComponents(nino, taxYear)(readsListString)
            .value
            .futureValue
            .swap
            .getOrElse(UpstreamErrorResponse("", OK))
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

      new TaiConnector(
        httpClient,
        serviceConfig,
        inject[HttpClientResponse],
        inject[ConfigDecorator],
        mockFeatureFlagService
      )
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(TaxComponentsRetrievalToggle)))
      .thenReturn(EitherT.rightT(FeatureFlag(TaxComponentsRetrievalToggle, isEnabled = true)))
  }

  "Calling TaiService.taxComponents" must {
    trait LocalSetup extends SpecSetup

    val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)

    val taxYear = TaxYear.now().getYear

    val url = s"/tai/$nino/tax-account/$taxYear/tax-components"

    "return bad gateway when the call results in a timeout" in new LocalSetup {
      stubWithDelay(url, OK, None, None, 100)

      val result: UpstreamErrorResponse =
        connector
          .taxComponents(nino, taxYear)(readsListString)
          .value
          .futureValue
          .swap
          .getOrElse(UpstreamErrorResponse("", OK))
      result.statusCode mustBe BAD_GATEWAY
    }
  }
}
