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

package services.partials

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import metrics.HasMetrics
import play.api.Mode.Mode
import play.api.mvc.RequestHeader
import play.api.{Configuration, Environment}
import services.http.WsAllMethods
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.partials.HtmlPartial
import util.{EnhancedPartialRetriever, Tools}

import scala.concurrent.Future
@Singleton
class PreferencesFrontendPartialService @Inject()(
  environment: Environment,
  configuration: Configuration,
  val http: WsAllMethods,
  val metrics: Metrics,
  applicationCrypto: ApplicationCrypto,
  val tools: Tools)
    extends EnhancedPartialRetriever(applicationCrypto) with HasMetrics with ServicesConfig {

  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = configuration
  val preferencesFrontendUrl = baseUrl("preferences-frontend")

  def getManagePreferencesPartial(returnUrl: String, returnLinkText: String)(
    implicit request: RequestHeader): Future[HtmlPartial] =
    loadPartial(s"$preferencesFrontendUrl/paperless/manage?returnUrl=${tools
      .encryptAndEncode(returnUrl)}&returnLinkText=${tools.encryptAndEncode(returnLinkText)}")

}
