/*
 * Copyright 2026 HM Revenue & Customs
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

package controllers.tempAddressFix

import cats.data.EitherT
import models.{Address, PersonDetails}
import models.tempAddressFix.AddressFixRecord
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import repositories.TempAddressFixRepository
import services.CitizenDetailsService
import connectors.CitizenDetailsConnector
import models.tempAddressFix.FixStatus
import com.google.inject.Inject
import models.tempAddressFix.FixStatus.{DoneCorrespondence, DoneResidential}
import play.api.Logging
import play.api.mvc.Results.{InternalServerError, NotFound}

import scala.concurrent.{ExecutionContext, Future}

class FixControllerHelper @Inject() (
  tempAddressFixRepository: TempAddressFixRepository,
  citizenDetailsService: CitizenDetailsService,
  citizenDetailsConnector: CitizenDetailsConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  def personDetailsWithResult(
    nino: String
  )(implicit request: Request[_], hc: HeaderCarrier): EitherT[Future, Result, PersonDetails] =
    citizenDetailsService.personDetails(Nino(nino)).transform {
      case Left(error)          => Left(InternalServerError(error.getMessage))
      case Right(None)          => Left(NotFound(s"details not found for nino $nino"))
      case Right(Some(details)) => Right(details)
    }

  def findOneAndUpdateWithResult(
    nino: String,
    newStatus: FixStatus,
    oldStatus: Option[FixStatus] = None
  ): EitherT[Future, Result, AddressFixRecord] =
    EitherT.fromOptionF(
      tempAddressFixRepository.findOneAndUpdate(nino, newStatus, oldStatus),
      NotFound("No record found to fix")
    )

  def updateAddressWithResult(nino: String, etag: String, address: Address, fixStatus: FixStatus)(implicit
    request: Request[_],
    hc: HeaderCarrier
  ): EitherT[Future, Result, FixStatus] =
    citizenDetailsConnector
      .updateAddress(Nino(nino), etag, address)
      .bimap(
        error => InternalServerError(error.message),
        _ => fixStatus
      )

  def processRecord(nino: String)(implicit request: Request[_], hc: HeaderCarrier): EitherT[Future, Result, FixStatus] =
    for {
      recordToFix <-
        findOneAndUpdateWithResult(nino, FixStatus.Processing, Some(FixStatus.Todo)) // Set record for processing
      details     <- personDetailsWithResult(nino) // get current address
      newStatus   <- fixAddress(recordToFix, details) // update address
      _           <- findOneAndUpdateWithResult(nino, newStatus) // set record as Done/Skipped
    } yield newStatus

  def fixAddress(record: AddressFixRecord, details: PersonDetails)(implicit
    request: Request[_],
    hc: HeaderCarrier
  ): EitherT[Future, Result, FixStatus] =
    if (details.address.flatMap(_.country).contains("ABROAD - NOT KNOWN")) {
      logger.info(s"residential address for nino ${record.nino} is ABROAD - NOT KNOWN and need fixing")
      val newAddress = details.address.map(_.copy(country = None, postcode = Some(record.postcode))).get
      updateAddressWithResult(record.nino, details.etag, newAddress, DoneResidential)
    } else if (details.correspondenceAddress.flatMap(_.country).contains("ABROAD - NOT KNOWN")) {
      logger.info(s"correspondence address for nino ${record.nino} is ABROAD - NOT KNOWN and need fixing")
      val newAddress = details.correspondenceAddress.map(_.copy(country = None, postcode = Some(record.postcode))).get
      updateAddressWithResult(record.nino, details.etag, newAddress, DoneCorrespondence)
    } else {
      logger.warn(
        s"Address is no longer ABROAD - NOT KNOWN for nino ${record.nino}, skipping record"
      )
      EitherT.rightT(FixStatus.SkippedNotAbroad)
    }

}
