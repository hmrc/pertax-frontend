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

package util

import java.net.{URI, URL, URLEncoder}

import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}

object Tools {
  def urlEncode(u: String): String = URLEncoder.encode(u, "UTF-8")
  def encryptAndEncode(s: String): String = urlEncode(ApplicationCrypto.QueryParameterCrypto.encrypt(PlainText(s)).value)
  def isRelative(url: String): Boolean = !new URI(url).isAbsolute && url.take(2) != "//"
}
