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
import config.LocalTemplateRenderer
import filters._
import models.LocalTaxYearResolver
import uk.gov.hmrc.play.frontend.filters.SessionCookieCryptoFilter
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.play.frontend.filters.{CSRFExceptionsFilter, CacheControlFilter, CookieCryptoFilter, DeviceIdFilter, HeadersFilter, SessionTimeoutFilter}

class LocalGuiceModule extends AbstractModule {
  override def configure() = {

    //These library components must be bound in this way, or using providers
    //bind(classOf[CookieCryptoFilter]).to(classOf[SessionCookieCryptoFilter])
    bind(classOf[HeadersFilter]).toInstance(HeadersFilter)
    bind(classOf[DeviceIdFilter]).toProvider(classOf[DeviceIdCookieFilterProvider])
    bind(classOf[CSRFExceptionsFilter]).toProvider(classOf[CSRFExceptionsFilterProvider])
    bind(classOf[SessionTimeoutFilter]).toProvider(classOf[SessionTimeoutFilterProvider])
    bind(classOf[CacheControlFilter]).toInstance(CacheControlFilter.fromConfig("caching.allowedContentTypes"))
    bind(classOf[TemplateRenderer]).to(classOf[LocalTemplateRenderer])
  }
}
