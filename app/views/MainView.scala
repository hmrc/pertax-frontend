/*
 * Copyright 2024 HM Revenue & Customs
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

package views

import com.google.inject.ImplementedBy
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import play.api.Logging
import play.api.i18n.Messages
import play.api.mvc.Request
import play.twirl.api.{Html, HtmlFormat}
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.hmrcfrontend.config.AccessibilityStatementConfig
import uk.gov.hmrc.hmrcfrontend.views.viewmodels.hmrcstandardpage.ServiceURLs
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.sca.models.BannerConfig
import uk.gov.hmrc.sca.services.WrapperService
import views.html.components.{AdditionalJavascript, HeadBlock}

import javax.inject.Inject
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[MainViewImpl])
trait MainView {
  // scalastyle:off parameter.number
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
    request: Request[_],
    messages: Messages
  ): HtmlFormat.Appendable
}

class MainViewImpl @Inject() (
  appConfig: ConfigDecorator,
  wrapperService: WrapperService,
  additionalScripts: AdditionalJavascript,
  headBlock: HeadBlock,
  accessibilityStatementConfig: AccessibilityStatementConfig
) extends MainView
    with Logging {

  // scalastyle:off parameter.number
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
    showUserResearchBanner: Boolean = false
  )(contentBlock: Html)(implicit request: Request[_], messages: Messages): HtmlFormat.Appendable = {

    val trustedHelper: Option[TrustedHelper] = Try(request.asInstanceOf[UserRequest[_]]) match {
      case Success(userRequest) => userRequest.trustedHelper
      case Failure(_)           => None
    }

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
        accessibilityStatementUrl = accessibilityStatementConfig.url
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
      optTrustedHelper = trustedHelper,
      fullWidth = fullWidth,
      hideMenuBar = hideAccountMenu,
      disableSessionExpired = disableSessionExpired
    )(messages, request)
  }
}
