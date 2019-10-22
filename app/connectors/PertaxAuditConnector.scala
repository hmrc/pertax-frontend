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

package connectors

import com.google.inject.{Inject, Singleton}
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.frontend.config.LoadAuditingConfig
@Singleton
class PertaxAuditConnector @Inject()(environment: Environment, configuration: Configuration)
    extends AuditConnector with AppName with RunMode {
  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = configuration
  val appNameConfiguration: Configuration = configuration
  override lazy val auditingConfig = LoadAuditingConfig("auditing")
}
