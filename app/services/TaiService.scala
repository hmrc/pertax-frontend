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

import cats.implicits.*
import com.google.inject.Inject
import connectors.TaiConnector
import models.admin.TaxComponentsRetrievalToggle
import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class TaiService @Inject() (taiConnector: TaiConnector, featureFlagService: FeatureFlagService)(implicit
  ec: ExecutionContext
) extends Logging
    with CurrentTaxYear {

  override def now: () => LocalDate = () => LocalDate.now()

  def getTaxComponentsList(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier,
    request: Request[?]
  ): Future[List[String]] =
    featureFlagService.get(TaxComponentsRetrievalToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        taiConnector
          .taxComponents(nino, year)
          .fold(_ => List.empty, _.as[List[String]](models.TaxComponents.readsListString))
      } else {
        logger.warn("Toggle TaxComponentsRetrievalToggle is disabled")
        Future.successful(List.empty)
      }
    }

  def isRecipientOfHicBc(nino: Nino)(implicit hc: HeaderCarrier, request: Request[?]): Future[Boolean] =
    featureFlagService.get(TaxComponentsRetrievalToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        taiConnector
          .taxComponents(nino, current.currentYear)
          .fold(_ => false, _.as[Boolean](models.TaxComponents.readsIsHICBCWithCharge))
      } else {
        logger.warn("Toggle TaxComponentsRetrievalToggle is disabled")
        Future.successful(false)
      }
    }
}
