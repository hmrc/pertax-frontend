/*
 * Copyright 2020 HM Revenue & Customs
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

import com.google.inject.Inject
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.renderer.TemplateRenderer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class LocalTemplateRenderer @Inject()(environment: Environment, configuration: Configuration, wsHttp: HttpClient)(
  implicit executionContext: ExecutionContext)
    extends TemplateRenderer with ServicesConfig {

  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = configuration
  override lazy val templateServiceBaseUrl = baseUrl("frontend-template-provider")
  override lazy val refreshAfter: Duration =
    runModeConfiguration.getInt("template.refreshInterval").getOrElse(600) seconds

  private implicit val hc = HeaderCarrier()

  override def fetchTemplate(path: String): Future[String] =
    wsHttp.GET(path).map(_.body)
}
