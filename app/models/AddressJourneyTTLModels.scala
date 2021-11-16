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

package models

import play.api.libs.json._
import reactivemongo.bson.BSONDateTime
import reactivemongo.play.json._

case class AddressJourneyTTLModel(nino: String, editedAddress: EditedAddress)

sealed trait EditedAddress {
  val expireAt: BSONDateTime
  def addressType: String
}

case class EditResidentialAddress(expireAt: BSONDateTime) extends EditedAddress {
  override def addressType: String = EditedAddress.editResidentialAddress
}
case class EditCorrespondenceAddress(expireAt: BSONDateTime) extends EditedAddress {
  override def addressType: String = EditedAddress.editCorrespondenceAddress
}

object EditedAddress {

  val editResidentialAddress: String = "EditResidentialAddress"
  val editCorrespondenceAddress: String = "EditCorrespondenceAddress"

  val addressType = "addressType"
  val expireAt = "expireAt"

  implicit val writes = new OWrites[EditedAddress] {
    def writes(model: EditedAddress): JsObject = Json.obj(
      addressType -> model.addressType,
      expireAt    -> model.expireAt
    )
  }

  implicit val reads = new Reads[EditedAddress] {
    override def reads(json: JsValue): JsResult[EditedAddress] =
      for {
        addressType <- (json \ addressType).validate[String]
        expireAt    <- (json \ expireAt).validate[BSONDateTime]
      } yield addressType match {
        case `editResidentialAddress`    => EditResidentialAddress(expireAt)
        case `editCorrespondenceAddress` => EditCorrespondenceAddress(expireAt)
      }
  }
}

object AddressJourneyTTLModel {
  implicit val format: OFormat[AddressJourneyTTLModel] = Json.format[AddressJourneyTTLModel]
}
