/*
 * Copyright 2023 HM Revenue & Customs
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

package views.html

import com.google.inject.ImplementedBy
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import play.api.Logging
import play.api.i18n.Messages
import play.twirl.api.{Html, HtmlFormat}
import uk.gov.hmrc.hmrcfrontend.views.viewmodels.hmrcstandardpage.ServiceURLs
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.sca.models.BannerConfig
import uk.gov.hmrc.sca.services.WrapperService
import views.html.components.{AdditionalJavascript, HeadBlock}

import javax.inject.Inject

@ImplementedBy(classOf[MainViewImpl])
trait MainView {
  def apply(
    pageTitle: String,
    serviceName: String = "label.your_personal_tax_account",
    sidebarContent: Option[Html] = None,
    showBackLink: Boolean = false,
    backLinkID: Boolean = true,
    backLinkUrl: String = "#",
    disableSessionExpired: Boolean = false,
    fullWidth: Boolean = false,
    stylesheets: Option[Html] = None,
    scripts: Option[Html] = None,
    accountHome: Boolean = false,
    messagesActive: Boolean = false,
    yourProfileActive: Boolean = false,
    hideAccountMenu: Boolean = false,
    showUserResearchBanner: Boolean = false
  )(contentBlock: Html)(implicit
    request: UserRequest[_],
    messages: Messages
  ): HtmlFormat.Appendable
}

class MainViewImpl @Inject() (
  appConfig: ConfigDecorator,
  wrapperService: WrapperService,
  additionalScripts: AdditionalJavascript,
  headBlock: HeadBlock
) extends MainView
    with Logging {

  override def apply(
    pageTitle: String,
    serviceName: String = "label.your_personal_tax_account",
    sidebarContent: Option[Html] = None,
    showBackLink: Boolean = false,
    backLinkID: Boolean = true,
    backLinkUrl: String = "#",
    disableSessionExpired: Boolean = false,
    fullWidth: Boolean = false,
    stylesheets: Option[Html] = None,
    scripts: Option[Html] = None,
    accountHome: Boolean = false,
    messagesActive: Boolean = false,
    yourProfileActive: Boolean = false,
    hideAccountMenu: Boolean = false,
    showUserResearchBanner: Boolean = true
  )(contentBlock: Html)(implicit request: UserRequest[_], messages: Messages): HtmlFormat.Appendable = {

    val fullPageTitle = s"$pageTitle - ${messages("label.your_personal_tax_account_gov_uk")}"

    logger.debug(s"SCA Wrapper layout used for request `${request.uri}``")
    wrapperService.standardScaLayout(
      content = contentBlock,
      pageTitle = Some(fullPageTitle),
      serviceNameKey = Some(messages(serviceName)),
      serviceURLs = ServiceURLs(
        serviceUrl = Some(appConfig.personalAccount),
        signOutUrl = Some(
          controllers.routes.ApplicationController
            .signout(Some(RedirectUrl(appConfig.getFeedbackSurveyUrl(appConfig.defaultOrigin))), None)
            .url
        ),
        accessibilityStatementUrl = Some(appConfig.accessibilityStatementUrl(request.uri))
      ),
      sidebarContent = sidebarContent,
      timeOutUrl = Some(controllers.routes.SessionManagementController.timeOut.url),
      keepAliveUrl = controllers.routes.SessionManagementController.keepAlive.url,
      showBackLinkJS = showBackLink,
      backLinkUrl = if (!backLinkUrl.equals("#")) Some(backLinkUrl) else None,
      scripts = Seq(additionalScripts(scripts)(request)),
      styleSheets = Seq(headBlock(stylesheets)(request)),
      bannerConfig = BannerConfig(
        showAlphaBanner = false,
        showBetaBanner = true,
        showHelpImproveBanner = showUserResearchBanner
      ),
      optTrustedHelper = request.trustedHelper,
      fullWidth = fullWidth,
      hideMenuBar = hideAccountMenu,
      disableSessionExpired = disableSessionExpired
    )(messages, request)
  }
}
