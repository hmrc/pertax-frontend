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

package filters

import javax.inject.{Inject, Provider, Singleton}
import connectors.PertaxAuditConnector
import play.api.Configuration
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.frontend.filters.DeviceIdCookieFilter


@Singleton
class DeviceIdCookieFilterProvider @Inject()(auditConnector: PertaxAuditConnector, val appNameConfiguration: Configuration) extends Provider[DeviceIdCookieFilter] with AppName {
  override def get(): DeviceIdCookieFilter = new DeviceIdCookieFilter(appName, auditConnector)
}
