/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.Configuration
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, SessionId}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.renderer.TemplateRenderer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class LocalTemplateRenderer @Inject() (
  configuration: Configuration,
  wsHttp: HttpClient,
  servicesConfig: ServicesConfig
)(implicit executionContext: ExecutionContext)
    extends TemplateRenderer {

  val runModeConfiguration: Configuration = configuration
  override lazy val templateServiceBaseUrl = servicesConfig.baseUrl("frontend-template-provider")
  override lazy val refreshAfter: Duration =
    runModeConfiguration.getOptional[Int]("template.refreshInterval").getOrElse(600) seconds

  private implicit val hc = HeaderCarrier()

  override def fetchTemplate(path: String): Future[String] =
    wsHttp.GET(path).map(_.body)
}
