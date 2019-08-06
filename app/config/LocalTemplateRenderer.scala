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

package config

import javax.inject.Inject
import play.api.{Configuration, Environment}
import play.api.Mode.Mode
import services.http.WsAllMethods
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.renderer.TemplateRenderer

import scala.concurrent.Future
import scala.concurrent.duration._

class LocalTemplateRenderer @Inject()(environment: Environment, configuration: Configuration, wsHttp: WsAllMethods)
    extends TemplateRenderer with ServicesConfig {

  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = configuration
  override lazy val templateServiceBaseUrl = baseUrl("frontend-template-provider")
  override lazy val refreshAfter: Duration =
    runModeConfiguration.getInt("template.refreshInterval").getOrElse(600) seconds

  private implicit val hc = HeaderCarrier()
  import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

  override def fetchTemplate(path: String): Future[String] =
    wsHttp.GET(path).map(_.body)
}
