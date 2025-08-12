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

package util

import com.google.inject.Inject
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.ApplicationCrypto

import java.net.{URI, URLEncoder}

class Tools @Inject() (val applicationCrypto: ApplicationCrypto) {
  def urlEncode(u: String): String        = URLEncoder.encode(u, "UTF-8")
  def encryptOnly(s: String): String      =
    applicationCrypto.QueryParameterCrypto.encrypt(PlainText(s)).value
  def encryptAndEncode(s: String): String =
    urlEncode(encryptOnly(s))
  def isRelative(url: String): Boolean    = !new URI(url).isAbsolute && url.take(2) != "//"
}
