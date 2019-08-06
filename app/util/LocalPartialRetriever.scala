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

import javax.inject.{Inject, Singleton}
import services.http.WsAllMethods
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.frontend.filters.SessionCookieCryptoFilter
import uk.gov.hmrc.play.partials.FormPartialRetriever
@Singleton
class LocalPartialRetriever @Inject()(override val httpGet: WsAllMethods, val applicationCrypto: ApplicationCrypto)
    extends FormPartialRetriever {

  val sessionCookieCryptoFilter: SessionCookieCryptoFilter = new SessionCookieCryptoFilter(applicationCrypto)
  override def crypto = sessionCookieCryptoFilter.encrypt
}
