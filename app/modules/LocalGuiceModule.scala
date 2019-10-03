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

package modules

import com.google.inject.AbstractModule
import config.{ApplicationCryptoProvider, LocalTemplateRenderer, SessionCookieCryptoFilterProvider}
import filters._
import services.http.WsAllMethods
import services.http.WSHttp
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.frontend.filters._
import uk.gov.hmrc.renderer.TemplateRenderer

class LocalGuiceModule extends AbstractModule {
  override def configure() = {
    bind(classOf[HeadersFilter]).toInstance(HeadersFilter)
    bind(classOf[DeviceIdFilter]).toProvider(classOf[DeviceIdCookieFilterProvider])
    bind(classOf[CSRFExceptionsFilter]).toProvider(classOf[CSRFExceptionsFilterProvider])
    bind(classOf[SessionTimeoutFilter]).toProvider(classOf[SessionTimeoutFilterProvider])
    bind(classOf[CacheControlFilter]).toInstance(CacheControlFilter.fromConfig("caching.allowedContentTypes"))
    bind(classOf[TemplateRenderer]).to(classOf[LocalTemplateRenderer])
    bind(classOf[ApplicationCrypto]).toProvider(classOf[ApplicationCryptoProvider])
    bind(classOf[CookieCryptoFilter]).toProvider(classOf[SessionCookieCryptoFilterProvider])
    bind(classOf[WSHttp]).to(classOf[WsAllMethods])
  }
}
