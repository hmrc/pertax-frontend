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

package models

import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class AddressJourneyTTLModel(nino: String, editedAddress: EditedAddress)

sealed trait EditedAddress {
  val expireAt: Instant
  def addressType: String
}

case class EditResidentialAddress(expireAt: Instant) extends EditedAddress {
  override def addressType: String = EditedAddress.editResidentialAddress
}
case class EditCorrespondenceAddress(expireAt: Instant) extends EditedAddress {
  override def addressType: String = EditedAddress.editCorrespondenceAddress
}

object EditedAddress extends MongoJavatimeFormats.Implicits {

  val editResidentialAddress: String = "EditResidentialAddress"
  val editCorrespondenceAddress: String = "EditCorrespondenceAddress"

  val addressType = "addressType"
  val expireAt = "expireAt"

  implicit val writes: OWrites[EditedAddress] = (model: EditedAddress) =>
    Json.obj(
      addressType -> model.addressType,
      expireAt    -> model.expireAt
    )

  implicit val reads: Reads[EditedAddress] = (json: JsValue) =>
    for {
      addressType <- (json \ addressType).validate[String]
      expireAt    <- (json \ expireAt).validate[Instant]
    } yield addressType match {
      case `editResidentialAddress`    => EditResidentialAddress(expireAt)
      case `editCorrespondenceAddress` => EditCorrespondenceAddress(expireAt)
    }
}

object AddressJourneyTTLModel {
  implicit val format: OFormat[AddressJourneyTTLModel] = Json.format[AddressJourneyTTLModel]
}
