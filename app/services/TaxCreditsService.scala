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
import cats.implicits.catsStdInstancesForFuture
import com.google.inject.Inject
import connectors.TaxCreditsConnector
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsService @Inject() (taxCreditsConnector: TaxCreditsConnector)(implicit ec: ExecutionContext) {

  def isAddressChangeInPTA(
    nino: Nino
  )(implicit headerCarrier: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Option[Boolean]] =
    taxCreditsConnector
      .getTaxCreditsExclusionStatus(nino)
      .transform {
        case Right(true)                                  =>
          Right(None) // We cannot know if the user needs to change its address on PTA or tax credits
        case Right(false)                                 => Right(Some(false))
        case Left(error) if error.statusCode == NOT_FOUND => Right(Some(true))
        case Left(error)                                  => Left(error)
      }
}
