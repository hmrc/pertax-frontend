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
import com.google.inject.Inject
import config.ConfigDecorator
import models.admin.FandFBannerToggle
import play.api.Logging
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

class FandFConnector @Inject() (
  val httpClientV2: HttpClientV2,
  httpClientResponse: HttpClientResponse,
  configDecorator: ConfigDecorator,
  featureFlagService: FeatureFlagService
) extends Logging {

  private lazy val baseUrl: String = configDecorator.fandfHost

  def showFandfBanner(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val url = s"$baseUrl/fandf/$nino/showBanner"

    featureFlagService.get(FandFBannerToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        val apiResponse: Future[Either[UpstreamErrorResponse, HttpResponse]] = httpClientV2
          .get(url"$url")(hc)
          .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
        httpClientResponse
          .read(apiResponse)
          .fold(_ => false, _.json.as[Boolean])
      } else {
        Future.successful(false)
      }
    }
  }

  def getFandFAccountDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, JsValue] = {
    val url = s"$baseUrl/fandf/$nino"

    httpClientResponse
      .read(
        httpClientV2
          .get(url"$url")(hc)
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map(_.json)
  }
}
