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
import models.PersonDetails
import models.tempAddressFix.AddressFixRecord
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import repositories.TempAddressFixRepository
import services.CitizenDetailsService
import connectors.CitizenDetailsConnector
import models.tempAddressFix.FixStatus
import com.google.inject.Inject
import play.api.Logging
import play.api.mvc.Results.{InternalServerError, NotFound, NotImplemented, Ok}

import scala.concurrent.{ExecutionContext, Future}

class FixControllerHelper @Inject() (
  tempAddressFixRepository: TempAddressFixRepository,
  citizenDetailsService: CitizenDetailsService,
  citizenDetailsConnector: CitizenDetailsConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  def processRecord(nino: String)(implicit request: Request[_], hc: HeaderCarrier): Future[Result] =
    tempAddressFixRepository.findOneAndUpdate(nino, FixStatus.Processing, Some(FixStatus.Todo)).flatMap {
      case None         =>
        logger.info(s"$nino was not found in the mongo collection")
        Future.successful(NotFound("No record found to fix"))
      case Some(record) =>
        logger.info(s"Fixing record for nino ${record.nino}")
        fixRecord(record).leftSemiflatMap { error =>
          tempAddressFixRepository.findOneAndUpdate(record.nino, FixStatus.Todo, Some(FixStatus.Processing)).map {
            case Some(_) => InternalServerError(error.message)
            case None    =>
              logger.error(s"Cannot find record for nino $nino and status processing")
              InternalServerError("Something is seriously wrong. unexpected status in mongo")
          }
        }.merge
    }

  def fixRecord(
    record: AddressFixRecord
  )(implicit request: Request[_], hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Result] =
    citizenDetailsService.personDetails(Nino(record.nino)).flatMap {
      case None          =>
        logger.error(s"Error nino ${record.nino} not found in citizen details")
        EitherT.rightT[Future, UpstreamErrorResponse](NotFound(s"nino ${record.nino} not found in citizen details"))
      case Some(details) =>
        fixAddress(record, details)
    }

  def fixAddress(record: AddressFixRecord, details: PersonDetails)(implicit
    request: Request[_],
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, Result] =
    if (details.address.flatMap(_.country).contains("ABROAD - NOT KNOWN")) {
      logger.info(
        s"residential address for nino ${record.nino} is ABROAD - NOT KNOWN and need fixing"
      )
      val newAddress = details.address.map(_.copy(country = None, postcode = Some(record.postcode))).get
      citizenDetailsConnector.updateAddress(Nino(record.nino), details.etag, newAddress).semiflatMap { _ =>
        tempAddressFixRepository.findOneAndUpdate(record.nino, FixStatus.DoneResidential).map { newRecord =>
          Ok(Json.toJson(newRecord))
        }
      }
    } else if (details.correspondenceAddress.flatMap(_.country).contains("ABROAD - NOT KNOWN")) {
      EitherT.rightT[Future, UpstreamErrorResponse](NotImplemented("not done yet"))
    } else {
      logger.warn(
        s"Address is no longer ABROAD - NOT KNOWN for nino ${record.nino}, skipping record"
      )
      EitherT.liftF(
        tempAddressFixRepository
          .findOneAndUpdate(record.nino, FixStatus.SkippedNotAbroad, Some(FixStatus.Processing))
          .map {
            case Some(newRecord) => Ok(Json.toJson(newRecord))
            case None            =>
              logger.error(s"Cannot find record for nino ${record.nino} and status processing")
              InternalServerError("Something is seriously wrong. unexpected status in mongo")
          }
      )
    }

}
