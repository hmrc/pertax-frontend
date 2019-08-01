/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait AddressChanged
object MovedToScotland extends AddressChanged
object MovedFromScotland extends AddressChanged
object AnyOtherMove extends AddressChanged

class AddressMovedService @Inject()(addressLookupService: AddressLookupService) {

  def moved(fromAddressId: String, toAddressId: String)(implicit hc: HeaderCarrier): Future[AddressChanged] = {
    for {
      fromResponse <- addressLookupService.lookup(fromAddressId)
      toResponse <- addressLookupService.lookup(toAddressId)
    } yield {
      (fromResponse, toResponse) match {
        case (AddressLookupSuccessResponse(fromRecordSet), AddressLookupSuccessResponse(toRecordSet)) =>
          val fromSub = fromRecordSet.addresses.headOption.map(_.address.subdivision)
          val toSub = toRecordSet.addresses.headOption.map(_.address.subdivision)

          val addressChanged = fromSub.flatMap(fromSubdivision =>
            toSub.map(toSubdivision =>
              if (hasMovedFromScotland(fromSubdivision, toSubdivision))
                MovedFromScotland
              else if (hasMovedToScotland(fromSubdivision, toSubdivision))
                MovedToScotland
              else
                AnyOtherMove
            )
          )

          addressChanged.getOrElse(AnyOtherMove)
        case _ =>
          AnyOtherMove
      }
    }
  }

  def toMessageKey(addressChanged: AddressChanged): Option[String] = {
    addressChanged match {
      case MovedFromScotland => Some("label.moved_from_scotland")
      case MovedToScotland => Some("label.moved_to_scotland")
      case AnyOtherMove => None
    }
  }

  private val scottishSubdivision = "GB-SCT"

  private def hasMovedFromScotland(fromSubdivision: String, toSubdivision: String): Boolean = {
    fromSubdivision == scottishSubdivision && toSubdivision != scottishSubdivision
  }

  private def hasMovedToScotland(fromSubdivision: String, toSubdivision: String): Boolean = {
    fromSubdivision != scottishSubdivision && toSubdivision == scottishSubdivision
  }

}
