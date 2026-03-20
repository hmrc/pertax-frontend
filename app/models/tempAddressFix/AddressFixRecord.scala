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
import play.api.libs.json.{Format, JsSuccess, Json, OFormat, Reads, Writes}
import uk.gov.hmrc.crypto.{Crypted, PlainText}

case class AddressFixRecord(nino: String, postcode: String, status: String)

object AddressFixRecord {
  implicit val formats: OFormat[AddressFixRecord] = Json.format[AddressFixRecord]

  def hash(value: String): String =
    java.security.MessageDigest
      .getInstance("SHA-256")
      .digest(value.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString

  def format(cryptoProvider: CryptoProvider): Format[AddressFixRecord] = {
    val crypto = cryptoProvider.get()

    val reads: Reads[AddressFixRecord] = Reads { json =>
      JsSuccess(Json.parse(crypto.decrypt(Crypted((json \ "data").as[String])).value).as[AddressFixRecord])
    }

    val writes: Writes[AddressFixRecord] = Writes { record =>
      Json.obj(
        "key"  -> hash(record.nino),
        "data" -> crypto.encrypt(PlainText(Json.toJson(record).toString)).value
      )
    }

    Format(reads, writes)
  }
}
