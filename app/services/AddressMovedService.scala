/*
 * Copyright 2021 HM Revenue & Customs
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
import models.{AddressChanged, AnyOtherMove, MovedFromScotland, MovedToScotland}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AddressMovedService @Inject()(addressLookupService: AddressLookupService) {

  def moved(fromAddressId: String, toAddressId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[AddressChanged] =
    withAddressExists(fromAddressId, toAddressId) {

      for {
        fromResponse <- addressLookupService.lookup(fromAddressId)
        toResponse   <- addressLookupService.lookup(toAddressId)
      } yield {
        (fromResponse, toResponse) match {
          case (AddressLookupSuccessResponse(fromRecordSet), AddressLookupSuccessResponse(toRecordSet)) =>
            val fromSubdivision = fromRecordSet.addresses.headOption.flatMap(_.address.subdivision)
            val toSubdivision = toRecordSet.addresses.headOption.flatMap(_.address.subdivision)

            if (hasMovedFromScotland(fromSubdivision, toSubdivision))
              MovedFromScotland
            else if (hasMovedToScotland(fromSubdivision, toSubdivision))
              MovedToScotland
            else
              AnyOtherMove
          case _ =>
            AnyOtherMove
        }
      }
    }

  def toMessageKey(addressChanged: AddressChanged): Option[String] =
    addressChanged match {
      case MovedFromScotland => Some("label.moved_from_scotland")
      case MovedToScotland   => Some("label.moved_to_scotland")
      case AnyOtherMove      => None
    }

  private val scottishSubdivision = "GB-SCT"

  private def hasMovedFromScotland(fromSubdivision: Option[String], toSubdivision: Option[String]): Boolean =
    fromSubdivision.contains(scottishSubdivision) && !toSubdivision.contains(scottishSubdivision)

  private def hasMovedToScotland(fromSubdivision: Option[String], toSubdivision: Option[String]): Boolean =
    !fromSubdivision.contains(scottishSubdivision) && toSubdivision.contains(scottishSubdivision)

  private def withAddressExists(fromAddressId: String, toAddressId: String)(
    f: => Future[AddressChanged]): Future[AddressChanged] =
    if (fromAddressId.trim.isEmpty || toAddressId.trim.isEmpty) Future.successful(AnyOtherMove) else f
}
