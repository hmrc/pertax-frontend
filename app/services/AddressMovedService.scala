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

import com.google.inject.Inject
import connectors.AddressLookupConnector
import models.addresslookup.Country
import models.{AddressChanged, AnyOtherMove, MovedFromScotland, MovedToScotland}
import uk.gov.hmrc.http.HeaderCarrier
import play.api.Logging
import util.PertaxValidators.PostcodeRegex

import scala.concurrent.{ExecutionContext, Future}

class AddressMovedService @Inject() (addressLookupService: AddressLookupConnector) extends Logging {

  private def isValidUkPostcode(p: String): Boolean = PostcodeRegex.pattern.matcher(p.trim.toUpperCase).matches()

  def moved(originalPostcode: String, newPostcode: String, p85Enabled: Boolean)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AddressChanged] =
    if (p85Enabled) {
      Future.successful(AnyOtherMove)
    } else {
      (originalPostcode, newPostcode) match {
        case (_, "")                         =>
          logger.error("New postcode is empty when checking for address move.")
          Future.successful(AnyOtherMove)
        case ("", _)                         => Future.successful(AnyOtherMove)
        case (originalPostcode, newPostcode)
            if !isValidUkPostcode(originalPostcode) || !isValidUkPostcode(newPostcode) =>
          Future.successful(AnyOtherMove)
        case (originalPostcode, newPostcode) =>
          (for {
            fromResponse <- addressLookupService.lookup(originalPostcode)
            toResponse   <- addressLookupService.lookup(newPostcode)
          } yield {
            val fromSubdivision = fromResponse.addresses.headOption.flatMap(_.address.subdivision)
            val toSubdivision   = toResponse.addresses.headOption.flatMap(_.address.subdivision)

            if (hasMovedFromScotland(fromSubdivision, toSubdivision)) {
              MovedFromScotland
            } else if (hasMovedToScotland(fromSubdivision, toSubdivision)) {
              MovedToScotland
            } else {
              AnyOtherMove
            }
          }).fold(
            error => {
              logger.error(s"Error looking up addresses for old or new postcode during address move check: $error")
              AnyOtherMove
            },
            identity
          )
      }
    }

  def toMessageKey(addressChanged: AddressChanged): Option[String] =
    addressChanged match {
      case MovedFromScotland => Some("label.moved_from_scotland")
      case MovedToScotland   => Some("label.moved_to_scotland")
      case AnyOtherMove      => None
    }

  private val scottishSubdivision = "GB-SCT"

  private def hasMovedFromScotland(fromSubdivision: Option[Country], toSubdivision: Option[Country]): Boolean =
    containsScottishSubdivision(fromSubdivision) && !containsScottishSubdivision(toSubdivision)

  private def hasMovedToScotland(fromSubdivision: Option[Country], toSubdivision: Option[Country]): Boolean =
    !containsScottishSubdivision(fromSubdivision) && containsScottishSubdivision(toSubdivision)

  private def containsScottishSubdivision(subdivision: Option[Country]): Boolean =
    subdivision.fold(false)(_.code.contains(scottishSubdivision))

}
