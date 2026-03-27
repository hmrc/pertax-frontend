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

package models.tempAddressFix

import config.CryptoProvider
import play.api.libs.json.{Format, JsObject, JsSuccess, JsValue, Json, OFormat, Reads, Writes}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import models.tempAddressFix.FixStatus

import java.time.Instant
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

case class AddressFixRecord(nino: String, postcode: String, status: FixStatus, timestamp: Instant = Instant.now) {
  def encryptedNino(cryptoProvider: CryptoProvider): String =
    AddressFixRecord.encryptedNino(nino, cryptoProvider)

  def obscuredNino: String     = nino.patch(3, "*" * 4, 4)
  def obscuredPostcode: String = postcode.dropRight(3) + "***"

  def obscuredId: String = s"nino: $obscuredNino, postcode: $obscuredPostcode"
}

object AddressFixRecord {
  implicit val formats: OFormat[AddressFixRecord] = Json.format[AddressFixRecord]
  implicit val formatInstant: Format[Instant]     = MongoJavatimeFormats.instantFormat

  def encryptedNino(nino: String, cryptoProvider: CryptoProvider): String = {
    val crypto = cryptoProvider.get()
    crypto.encrypt(PlainText(nino)).value
  }

  def format(cryptoProvider: CryptoProvider): Format[AddressFixRecord] = {
    implicit val crypto: Encrypter & Decrypter = cryptoProvider.get()

    implicit def reads(implicit
      crypto: Encrypter with Decrypter
    ): Reads[AddressFixRecord] =
      Reads[AddressFixRecord] { json =>
        val nino      = crypto.decrypt(Crypted((json \ "data" \ "nino").as[String])).value
        val postcode  = crypto.decrypt(Crypted((json \ "data" \ "postcode").as[String])).value
        // (json \ "data" \ "status").as[FixStatus](FixStatus.format) does not work: Scala 3 enums are being treated as anonymous subclasses by the BSON layer
        val status    = FixStatus.valueOf((json \ "data" \ "status").as[String])
        val timestamp = (json \ "data" \ "timestamp").as[Instant]

        JsSuccess(AddressFixRecord(nino, postcode, status, timestamp))
      }

    implicit def writes(implicit crypto: Encrypter): Writes[AddressFixRecord] =
      Writes[AddressFixRecord] { (address: AddressFixRecord) =>
        Json.obj(
          "data" -> Json.obj(
            "nino"      -> crypto.encrypt(PlainText(address.nino)).value,
            "postcode"  -> crypto.encrypt(PlainText(address.postcode)).value,
            "status"    -> address.status.toString,
            "timestamp" -> address.timestamp
          )
        )
      }

    Format(reads, writes)
  }
}

case class AddressFixRecordRequest(nino: String, postcode: String) {
  def toAddressRecord: AddressFixRecord = AddressFixRecord(nino, postcode, FixStatus.Backlog)
}

object AddressFixRecordRequest {
  implicit val reads: Reads[AddressFixRecordRequest] = Json.reads[AddressFixRecordRequest]
}
