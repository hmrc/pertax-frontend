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

import javax.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import metrics.HasMetrics
import play.api.Configuration
import play.api.Mode.Mode
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.RequestHeader
import services.http.WsAllMethods
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.filters.SessionCookieCryptoFilter
import uk.gov.hmrc.play.partials.HtmlPartial
import util.{EnhancedPartialRetriever, Tools}

import scala.concurrent.Future


@Singleton
class SaPartialService @Inject() (val mode:Mode, val runModeConfiguration: Configuration, override val http: WsAllMethods, override val messagesApi: MessagesApi, val metrics: Metrics, val configDecorator: ConfigDecorator, sessionCookieCryptoFilter: SessionCookieCryptoFilter, val tools: Tools) extends EnhancedPartialRetriever(sessionCookieCryptoFilter) with HasMetrics with ServicesConfig with I18nSupport {


  private val returnUrl = configDecorator.pertaxFrontendHomeUrl
  private val returnLinkText = Messages("label.back_to_account_home")  //TODO remove ref to Messages as this is the service layer

  def getSaAccountSummary(implicit request: RequestHeader): Future[HtmlPartial] = {
    loadPartial(configDecorator.businessTaxAccountService + s"/business-account/partial/sa/account-summary?returnUrl=${tools.urlEncode(returnUrl)}&returnLinkText=${tools.urlEncode(returnLinkText)}")
  }

}
