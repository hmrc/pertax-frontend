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

import cats.data.OptionT
import cats.implicits.catsStdInstancesForFuture
import com.google.inject.Inject
import connectors.TaxCreditsConnector
import play.api.http.Status.{NOT_FOUND, OK}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsService @Inject() (taxCreditsConnector: TaxCreditsConnector)(implicit ec: ExecutionContext) {

  def checkForTaxCredits(nino: Option[Nino])(implicit headerCarrier: HeaderCarrier): OptionT[Future, Boolean] =
    if (nino.isEmpty) {
      OptionT.some(false)
    } else {
      OptionT(
        taxCreditsConnector
          .checkForTaxCredits(nino.get)
          .fold(
            error => if (error.statusCode == NOT_FOUND) Some(false) else None,
            result =>
              if (result.status == OK) Some(true)
              else Some(false)
          )
      )
    }
}
