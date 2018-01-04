/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import play.api.mvc.Request
import services.http.WsAllMethods
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.partials.HtmlPartial
import util.EnhancedPartialRetriever

import scala.concurrent.Future


@Singleton
class CspPartialService @Inject() (val http: WsAllMethods, val metrics: Metrics) extends EnhancedPartialRetriever with HasMetrics with ServicesConfig {

  lazy val serviceUrl = baseUrl("csp-partials")

  def webchatClickToChatScriptPartial(sourceService: String)(implicit request: Request[_]): Future[HtmlPartial] = {

    val entryPoint = getConfString(s"csp-partials.$sourceService.entryPoint", throw new RuntimeException("Missing entryPoint csp config"))
    val template = getConfString(s"csp-partials.$sourceService.template", throw new RuntimeException("Missing template csp config"))

    loadPartial(serviceUrl + s"/csp-partials/webchat-click-to-chat/$entryPoint/$template")
  }

}
