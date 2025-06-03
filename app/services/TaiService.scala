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

package services

import cats.data.EitherT
import com.google.inject.Inject
import connectors.TaiConnector
import models.TaxComponents
import models.admin.TaxComponentsToggle
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

class TaiService @Inject() (taiConnector: TaiConnector, featureFlagService: FeatureFlagService)(implicit
  ec: ExecutionContext
) {

  def retrieveTaxComponentsState(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): Future[List[String]] = get(nino, year)
    .fold(
      _ => List.empty,
      taxComponents => taxComponents
    )

  def get(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, List[String]] = {
    val result: Future[Either[UpstreamErrorResponse, List[String]]] =
      featureFlagService.get(TaxComponentsToggle).flatMap { toggle =>
        if (toggle.isEnabled) {
          taiConnector
            .taxComponents(nino, year)
            .map(result => TaxComponents.fromJsonTaxComponents(result.json))
            .value
        } else {
          Future.successful(Right(List.empty))
        }
      }
    EitherT(result)
  }
}
