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

package services.http

import akka.actor.ActorSystem
import com.typesafe.config.Config
import connectors.PertaxAuditConnector
import com.google.inject.{Inject, Singleton}
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Play}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.http.ws._

trait WSHttp
    extends HttpGet with WSGet with HttpPut with WSPut with HttpPost with WSPost with HttpDelete with WSDelete
    with HttpPatch with WSPatch

@Singleton
class WsAllMethods @Inject()(
  environment: Environment,
  config: Configuration,
  override val auditConnector: PertaxAuditConnector)
    extends WSHttp with HttpAuditing with AppName with RunMode {

  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = config
  val appNameConfiguration: Configuration = config

  override val hooks = Seq(AuditingHook)

  override protected def actorSystem: ActorSystem = Play.current.actorSystem

  override protected def configuration: Option[Config] = Some(Play.current.configuration.underlying)
}
