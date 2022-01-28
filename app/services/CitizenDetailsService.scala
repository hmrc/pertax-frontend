/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import connectors.{CitizenDetailsConnector, PersonDetailsErrorResponse, PersonDetailsHiddenResponse, PersonDetailsNotFoundResponse, PersonDetailsSuccessResponse, PersonDetailsUnexpectedResponse}
import controllers.auth.requests.UserRequest
import controllers.bindable.{AddressStatus, InvalidAddress, ValidAddressesBothInterrupt, ValidAddressesCorrespondanceInterrupt, ValidAddressesNoInterrupt, ValidAddressesResidentialInterrupt}
import models.Address
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

/*
There is an issue with NINOs obtained from Auth/IV where the suffix is incorrect,
when we display a NINO to a user we always want to obtain the NINO from CID where it
matches the NINO ignoring the suffix (and audits the mismatch) and retrieves the
correct full NINO. We can't use this call downstream as we are not sure how the suffix
will be treated in the HODs/DES layer.
 */

@Singleton
class CitizenDetailsService @Inject() (
  configDecorator: ConfigDecorator,
  citizenDetailsConnector: CitizenDetailsConnector
)(implicit
  ec: ExecutionContext
) {

  def getNino(implicit request: UserRequest[_], hc: HeaderCarrier): Future[Option[Nino]] =
    if (configDecorator.getNinoFromCID) {
      request.nino match {
        case Some(nino) =>
          for {
            result <- citizenDetailsConnector.personDetails(nino)
          } yield result match {
            case PersonDetailsSuccessResponse(personDetails) => personDetails.person.nino
            case _                                           => None
          }
        case _ => Future.successful(None)
      }
    } else {
      Future.successful(request.nino)
    }

  def getAddressStatusFromPersonalDetails(implicit
    request: UserRequest[_],
    hc: HeaderCarrier
  ): Future[AddressStatus] =
    if (configDecorator.getAddressStatusFromCID) {
      request.personDetails match {
        case Some(details) =>
          val residentialAddressStatus = if (details.address.isDefined) getAddressStatus(details.address.get) else None
          val correspondanceAddressStatus =
            if (details.correspondenceAddress.isDefined) getAddressStatus(details.correspondenceAddress.get) else None
          Future.successful(addressStatusParse(Tuple2(residentialAddressStatus, correspondanceAddressStatus)))
        case _ => Future.successful(InvalidAddress)
      }
    } else {
      Future.successful(ValidAddressesNoInterrupt)
    }

  private def getAddressStatus(address: Address): Option[Int] =
    if (address.status.isDefined) {
      address.status.get match {
        case result if result == 0 || result == 1 =>
          Some(result)
        case _ => None
      }
    } else {
      None
    }

  private def addressStatusParse(statuses: Tuple2[Option[Int], Option[Int]]): AddressStatus = {
    statuses match {
      case (Some(0), Some(0)) => ValidAddressesNoInterrupt
      case (Some(1), Some(1)) => ValidAddressesBothInterrupt
      case (Some(1), Some(0)) => ValidAddressesResidentialInterrupt
      case (Some(0), Some(1)) => ValidAddressesCorrespondanceInterrupt
      case _ => InvalidAddress
    }
  }
}
