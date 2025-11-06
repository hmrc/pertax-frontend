/*
 * Copyright 2025 HM Revenue & Customs
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
import org.mockito.Mockito.when
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

class DefaultTaiConnectorSpec extends ConnectorSpec with WireMockHelper with IntegrationPatience {

  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  override implicit lazy val app: Application =
    app(
      Map(
        "microservice.services.tai.port" -> server.port(),
        "tai.timeoutInMilliseconds"      -> 1000
      ),
      bind[FeatureFlagService].toInstance(mockFeatureFlagService)
    )

  def connector: DefaultTaiConnector =
    app.injector.instanceOf[DefaultTaiConnector]

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private val nino = Nino("AA123456A")
  private val year = 2024
  private val url  = s"/tai/${nino.nino}/tax-account/$year/tax-components"

  private def toggle(enabled: Boolean) =
    EitherT.rightT[scala.concurrent.Future, UpstreamErrorResponse](
      FeatureFlag(models.admin.TaxComponentsRetrievalToggle, enabled)
    )

  "DefaultTaiConnector.taxComponents" must {

    "return Right(Some(json)) when toggle enabled" in {
      when(mockFeatureFlagService.getAsEitherT(models.admin.TaxComponentsRetrievalToggle))
        .thenReturn(toggle(enabled = true))

      stubGet(url, OK, Some("""{"x": "some Json"}"""))

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe Right(Some(true))
    }

    "return Right(None) when toggle enabled but JSON does not validate to A" in {
      when(mockFeatureFlagService.getAsEitherT(models.admin.TaxComponentsRetrievalToggle))
        .thenReturn(toggle(enabled = true))

      stubGet(url, OK, Some("""invalid json"""))

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe Right(None)
    }

    "return Left(UpstreamErrorResponse) when downstream returns 5xx" in {
      when(mockFeatureFlagService.getAsEitherT(models.admin.TaxComponentsRetrievalToggle))
        .thenReturn(toggle(enabled = true))

      stubGet(url, INTERNAL_SERVER_ERROR, None)

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, _]]
    }

    "Return Right(None) when toggle disabled (no HTTP call)" in {
      when(mockFeatureFlagService.getAsEitherT(models.admin.TaxComponentsRetrievalToggle))
        .thenReturn(toggle(enabled = false))

      val result =
        connector.taxComponents(nino, year).value.futureValue

      result mustBe Right(None)
    }
  }
}
