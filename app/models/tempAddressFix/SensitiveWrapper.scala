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

import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText, Sensitive}
import play.api.libs.json.*

import scala.util.Try

case class SensitiveWrapper[T](override val decryptedValue: T) extends Sensitive[T]

object SensitiveWrapper {

  implicit def reads[T](implicit reads: Reads[T], crypto: Encrypter with Decrypter): Reads[SensitiveWrapper[T]] =
    Reads[SensitiveWrapper[T]] { json =>
      def decryptValue(value: JsValue): JsValue = value match {
        case JsString(encryptedStr) =>
          val jsNumberRegex     = """(-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?)""".r
          val decrypted: String = Try {
            crypto.decrypt(Crypted(encryptedStr)).value
          }.getOrElse(
            encryptedStr
          ) // fallback to the original is decryption fails. When encrypting an existing collection it allows reading the unencrypted data

          // once encrypted all values are string and the original type is lost
          // The below recover the original type although it will fail if for some reason a number was inside quotes.
          decrypted match {
            case s if jsNumberRegex.matches(s) => JsNumber(BigDecimal(decrypted))
            case "true"                        => JsBoolean(true)
            case "false"                       => JsBoolean(false)
            case "null"                        => JsNull
            case _                             => JsString(decrypted)
          }

        case JsObject(fields) =>
          JsObject(fields.map { case (k, v) => k -> decryptValue(v) })

        case JsArray(values) =>
          JsArray(values.map(decryptValue))

        case other =>
          other // JsNull and other non-encrypted values stay as-is
      }

      val decryptedJson = decryptValue(json)
      decryptedJson.validate[T](reads).map(SensitiveWrapper(_))
    }

  implicit def writes[T](implicit writes: Writes[T], crypto: Encrypter): Writes[SensitiveWrapper[T]] =
    Writes[SensitiveWrapper[T]] { (wrapper: SensitiveWrapper[T]) =>
      val jsValue: JsValue = Json.toJson(wrapper.decryptedValue)(writes)

      def encryptValue(value: JsValue): JsValue = value match {
        case JsString(str) =>
          JsString(crypto.encrypt(PlainText(str)).value)

        case JsNumber(num) =>
          JsString(crypto.encrypt(PlainText(num.toString)).value)

        case JsBoolean(bool) =>
          JsString(crypto.encrypt(PlainText(bool.toString)).value)

        case JsObject(fields) =>
          JsObject(fields.map { case (k, v) => k -> encryptValue(v) })

        case JsArray(values) =>
          JsArray(values.map(encryptValue))

        case JsNull =>
          JsNull

        case other =>
          JsString(crypto.encrypt(PlainText(Json.stringify(other))).value)

      }

      encryptValue(jsValue) match {
        case obj: JsObject => obj
        case other         => throw new IllegalArgumentException(s"Expected JsObject but got ${other.getClass}")
      }
    }

}
