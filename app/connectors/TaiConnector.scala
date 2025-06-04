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
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import models.admin.TaxComponentsToggle
import play.api.libs.json.Reads
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaiConnector @Inject() (
  val httpClientV2: HttpClientV2,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse,
  configDecorator: ConfigDecorator,
  featureFlagService: FeatureFlagService
) {

  private lazy val taiUrl                              = servicesConfig.baseUrl("tai")
  def taxComponents[A](nino: Nino, year: Int)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    reads: Reads[A]
  ): EitherT[Future, UpstreamErrorResponse, Option[A]] =
    featureFlagService.getAsEitherT(TaxComponentsToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        val url = s"$taiUrl/tai/$nino/tax-account/$year/tax-components"
        httpClientResponse
          .read(
            httpClientV2
              .get(url"$url")
              .transform(_.withRequestTimeout(configDecorator.taiTimeoutInMilliseconds.milliseconds))
              .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
          )
          .map(result => result.json.asOpt[A](reads))
      } else {
        EitherT.right[UpstreamErrorResponse](Future.successful[Option[A]](None))
      }
    }

}
