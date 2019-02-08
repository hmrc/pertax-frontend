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

package playconfig

import config.StaticGlobalDependencies
import org.slf4j.MDC
import play.api.Mode.Mode
import play.api._
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.play.frontend.bootstrap.Routing.RemovingOfTrailingSlashes
import uk.gov.hmrc.play.graphite.GraphiteConfig
import uk.gov.hmrc.play.frontend.config.ErrorAuditingSettings


object ApplicationGlobal extends GlobalSettings with GraphiteConfig
    with RemovingOfTrailingSlashes with ErrorAuditingSettings with RunMode {


  override protected def mode: Mode = Play.current.mode
  override protected def runModeConfiguration: Configuration = Play.current.configuration

  private lazy val configuration = StaticGlobalDependencies.deps.configuration
  override lazy val auditConnector = StaticGlobalDependencies.deps.pertaxAuditConnector

  lazy val enableSecurityHeaderFilter: Boolean = configuration.getBoolean("security.headers.filter.enabled").getOrElse(true)
  lazy val loggerDateFormat: Option[String] = configuration.getString("logger.json.dateformat")

  override def onStart(app: Application) {
    Logger.info(s"Starting frontend : $appName : in mode : ${app.mode}")
    MDC.put("appName", appName)
    loggerDateFormat.foreach(str => MDC.put("logger.json.dateformat", str))
    super.onStart(app)
  }

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig("microservice.metrics")
  override def appName: String = configuration.getString("appName").getOrElse("APP NAME NOT SET")

}
