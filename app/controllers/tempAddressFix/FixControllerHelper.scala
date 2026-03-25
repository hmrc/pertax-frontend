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
import models.tempAddressFix.{AddressFixRecord, ErrorResult, FixStatus}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import repositories.{EditAddressLockRepository, TempAddressFixRepository}
import services.CitizenDetailsService
import connectors.CitizenDetailsConnector
import com.google.inject.Inject
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import models.tempAddressFix.FixStatus.{DoneCorrespondence, DoneResidential}
import play.api.Logging
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.mvc.Request

import scala.concurrent.{ExecutionContext, Future}

class FixControllerHelper @Inject() (
  tempAddressFixRepository: TempAddressFixRepository,
  citizenDetailsService: CitizenDetailsService,
  citizenDetailsConnector: CitizenDetailsConnector,
  editAddressLockRepository: EditAddressLockRepository
)(implicit ec: ExecutionContext)
    extends Logging {

  def personDetailsWithErrorResult(
    nino: String
  )(implicit request: Request[_], hc: HeaderCarrier): EitherT[Future, ErrorResult, PersonDetails] =
    citizenDetailsService.personDetails(Nino(nino)).transform {
      case Left(error)          => Left(ErrorResult(INTERNAL_SERVER_ERROR, error.getMessage))
      case Right(None)          => Left(ErrorResult(NOT_FOUND, s"details not found for nino $nino"))
      case Right(Some(details)) => Right(details)
    }

  def findOneAndUpdateWithErrorResult(
    nino: String,
    newStatus: FixStatus,
    oldStatus: Option[FixStatus] = None
  ): EitherT[Future, ErrorResult, AddressFixRecord] =
    EitherT.fromOptionF(
      tempAddressFixRepository.findOneAndUpdate(nino, newStatus, oldStatus),
      ErrorResult(NOT_FOUND, "No record found to fix")
    )

  def updateAddressWithErrorResult(record: AddressFixRecord, etag: String, address: Address, fixStatus: FixStatus)(
    implicit
    request: Request[_],
    hc: HeaderCarrier
  ): EitherT[Future, ErrorResult, FixStatus] =
    citizenDetailsConnector
      .updateAddress(Nino(record.nino), etag, address)
      .bimap(
        error => ErrorResult(INTERNAL_SERVER_ERROR, error.message),
        _ =>
          logger.info(s"Address updated successfully in NPS for ${record.obscuredId}")
          fixStatus
      )

  def processRecord(
    nino: String
  )(implicit request: Request[_], hc: HeaderCarrier): EitherT[Future, ErrorResult, FixStatus] =
    for {
      recordToFix <-
        findOneAndUpdateWithErrorResult(
          nino,
          FixStatus.Processing,
          Some(FixStatus.Todo)
        ) // Set record for processing
      details     <- personDetailsWithErrorResult(nino) // get current address
      newStatus   <- fixAddress(recordToFix, details) // update address
      _           <- EitherT.rightT(lockAddress(nino, newStatus)) // lock the address that was updated
      _           <- findOneAndUpdateWithErrorResult(nino, newStatus) // set record as Done/Skipped
    } yield newStatus

  def fixAddress(record: AddressFixRecord, details: PersonDetails)(implicit
    request: Request[_],
    hc: HeaderCarrier
  ): EitherT[Future, ErrorResult, FixStatus] =
    if (details.address.flatMap(_.country).contains("ABROAD - NOT KNOWN")) {
      logger.info(s"residential address for ${record.obscuredId} is ABROAD - NOT KNOWN and need fixing")
      val newAddress = details.address
        .map(addr =>
          addr.copy(country = None, postcode = Some(record.postcode), startDate = addr.startDate.map(_.plusDays(1)))
        )
        .get
      updateAddressWithErrorResult(record, details.etag, newAddress, DoneResidential)
    } else if (details.correspondenceAddress.flatMap(_.country).contains("ABROAD - NOT KNOWN")) {
      logger.info(s"correspondence address for ${record.obscuredId} is ABROAD - NOT KNOWN and need fixing")
      val newAddress = details.correspondenceAddress
        .map(addr =>
          addr.copy(country = None, postcode = Some(record.postcode), startDate = addr.startDate.map(_.plusDays(1)))
        )
        .get
      updateAddressWithErrorResult(record, details.etag, newAddress, DoneCorrespondence)
    } else {
      logger.warn(
        s"Address is no longer ABROAD - NOT KNOWN for ${record.obscuredId}, skipping record"
      )
      EitherT.rightT(FixStatus.SkippedNotAbroad)
    }

  private def lockAddress(nino: String, status: FixStatus): Future[Boolean] = status match {
    case DoneResidential    => editAddressLockRepository.insert(nino.take(8), ResidentialAddrType)
    case DoneCorrespondence => editAddressLockRepository.insert(nino.take(8), PostalAddrType)
    case _                  => Future.successful(false)
  }

}
