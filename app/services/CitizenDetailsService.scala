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
import connectors.CitizenDetailsConnector
import models.admin.GetPersonFromCitizenDetailsToggle
import models.{Address, MatchingDetails, PersonDetails}
import play.api.Logging
import play.api.http.Status.CONFLICT
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

class CitizenDetailsService @Inject() (
  citizenDetailsConnector: CitizenDetailsConnector,
  featureFlagService: FeatureFlagService
) extends Logging {

  def personDetails(
    nino: Nino,
    refreshCache: Boolean = false
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]] =
    for {
      toggle <- EitherT.liftF(featureFlagService.get(GetPersonFromCitizenDetailsToggle))
      result <- if (toggle.isEnabled) {
                  citizenDetailsConnector
                    .personDetails(nino, refreshCache)
                    .map(jsValue => Some(jsValue.as[PersonDetails]))
                } else {
                  logger.info(s"Feature flag disabled for nino: ${nino.value}")
                  EitherT.rightT[Future, UpstreamErrorResponse](None)
                }
    } yield result

  def updateAddress(nino: Nino, newAddress: Address, currentPersonDetails: PersonDetails, tries: Int = 0)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Boolean] = {

    def compareAndRetry(
      currentPersonDetails: PersonDetails,
      newPersonDetailsOption: Option[PersonDetails],
      error: UpstreamErrorResponse
    ): EitherT[Future, UpstreamErrorResponse, Boolean] =
      newPersonDetailsOption.fold(EitherT.leftT[Future, Boolean](error)) { newPersonDetails =>
        if (currentPersonDetails.address == newPersonDetails.address) {
          // Address has not changed in NPS, it is safe to retry with the new etag
          updateAddress(nino, newAddress, newPersonDetails, tries = tries + 1)
        } else {
          EitherT.leftT[Future, Boolean](error)
        }
      }

    citizenDetailsConnector
      .updateAddress(nino: Nino, currentPersonDetails.etag: String, newAddress: Address)
      .leftFlatMap { error =>
        if (error.statusCode == CONFLICT && tries == 0) {
          logger.warn(s"Precondition failed when updating address, retrying once...")
          for {
            newPersonDetailsOption <- personDetails(nino, refreshCache = true)
            result                 <- compareAndRetry(currentPersonDetails, newPersonDetailsOption, error)
          } yield result
        } else {
          EitherT.leftT[Future, Boolean](error)
        }
      }
  }

  def getMatchingDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, MatchingDetails] =
    citizenDetailsConnector
      .getMatchingDetails(nino)
      .map { response =>
        MatchingDetails.fromJsonMatchingDetails(response.json)
      }

  def clearCachedPersonDetails(nino: Nino)(implicit request: Request[_]): Future[Unit] =
    citizenDetailsConnector.clearPersonDetailsCache(nino)
}
