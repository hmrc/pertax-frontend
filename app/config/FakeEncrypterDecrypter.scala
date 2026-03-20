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

package config

import uk.gov.hmrc.crypto.*

class FakeEncrypterDecrypter extends Encrypter with Decrypter {
  override def encrypt(plain: PlainContent): Crypted = plain match {
    case PlainText(text)   => Crypted(text)
    case PlainBytes(bytes) => Crypted(new String(bytes, "UTF-8"))
  }

  override def decrypt(reversiblyEncrypted: Crypted): PlainText =
    PlainText(reversiblyEncrypted.value)

  override def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes =
    PlainBytes(reversiblyEncrypted.value.getBytes("UTF-8"))
}
