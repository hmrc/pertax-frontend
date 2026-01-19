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

import com.github.tomakehurst.wiremock.client.WireMock.{getRequestedFor, urlEqualTo}
import models.admin.FandFBannerToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import play.api.libs.json.Json
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.Future
import scala.util.Random

class FandfConnectorSpec extends ConnectorSpec with WireMockHelper with MockitoSugar {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Map("microservice.services.fandf.port" -> server.port()))
    .overrides(api.inject.bind[FeatureFlagService].toInstance(mockFeatureFlagService))
    .build()

  private val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)
  private val url        = s"/fandf/$nino/showBanner"

  private def connector: FandFConnector = app.injector.instanceOf[FandFConnector]

  override def beforeEach() = {
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(FandFBannerToggle)))
      .thenReturn(Future.successful(FeatureFlag(FandFBannerToggle, isEnabled = true)))
  }

  "showFandfBanner is called" must {
    "return a true right response if returned in the json body" in {
      stubGet(url, OK, Some(Json.toJson(true).toString))

      val result = connector.showFandfBanner(nino).futureValue
      result mustBe true
    }

    "return a false right response" in {
      stubGet(url, OK, Some(Json.toJson(false).toString))

      val result = connector.showFandfBanner(nino).futureValue

      result mustBe false
    }

    "return a false response when toggle is off" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(FandFBannerToggle)))
        .thenReturn(Future.successful(FeatureFlag(FandFBannerToggle, isEnabled = false)))

      val result = connector.showFandfBanner(nino).futureValue

      result mustBe false
      server.verify(
        0,
        getRequestedFor(
          urlEqualTo(url)
        )
      )
    }

    List(SERVICE_UNAVAILABLE, IM_A_TEAPOT, BAD_REQUEST).foreach { httpResponse =>
      s"return false when $httpResponse status is received" in {
        stubGet(url, httpResponse, Some("nonsense response"))

        val result = connector.showFandfBanner(nino).futureValue
        result mustBe false
      }
    }
  }
}
