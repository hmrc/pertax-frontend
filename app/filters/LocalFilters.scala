/*
 * Copyright 2017 HM Revenue & Customs
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

package filters

import javax.inject.Inject

import com.kenshoo.play.metrics.MetricsFilter
import play.api.http.DefaultHttpFilters
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.play.filters.CacheControlFilter
import uk.gov.hmrc.play.filters.frontend._


class LocalFilters @Inject() (
  securityHeadersFilter: SecurityHeadersFilter,
  metricsFilter: MetricsFilter,
  headersFilter: HeadersFilter,
  testFilter: CookieCryptoFilter,
  deviceIdFilter: DeviceIdFilter,
  loggingFilter: LocalLoggingFilter,
  frontendAuditFilter: LocalFrontendAuditFilter,
  sessionTimeoutFilter: SessionTimeoutFilter,
  csrfExceptionsFilter: CSRFExceptionsFilter,
  csrfFilter: CSRFFilter,
  cacheControlFilter: CacheControlFilter
) extends DefaultHttpFilters(metricsFilter, headersFilter, testFilter,
    deviceIdFilter, loggingFilter, frontendAuditFilter, sessionTimeoutFilter,
    csrfExceptionsFilter, csrfFilter, cacheControlFilter)
