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
import controllers.auth.requests.{AuthenticatedRequest, UserRequest}
import models.admin.SCAWrapperToggle
import play.api.Logging
import play.api.i18n.Messages
import play.api.mvc.Request
import play.twirl.api.{Html, HtmlFormat}
import services.admin.FeatureFlagService
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.sca.models.BannerConfig
import uk.gov.hmrc.sca.services.WrapperService
import views.html.OldMainView
import views.html.components.{AdditionalJavascript, HeadBlock}

import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.{Failure, Success, Try}

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
    showChildBenefitBanner: Boolean = false,
    showUserResearchBanner: Boolean = false
  )(contentBlock: Html)(implicit
    request: UserRequest[_],
    messages: Messages
  ): HtmlFormat.Appendable
}

class MainViewImpl @Inject() (
  appConfig: ConfigDecorator,
  featureFlagService: FeatureFlagService,
  wrapperService: WrapperService,
  oldLayout: OldMainView,
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
    showChildBenefitBanner: Boolean = false,
    showUserResearchBanner: Boolean = false
  )(contentBlock: Html)(implicit request: UserRequest[_], messages: Messages): HtmlFormat.Appendable = {
    val scaWrapperToggle =
      Await.result(featureFlagService.get(SCAWrapperToggle), Duration(appConfig.SCAWrapperFutureTimeout, SECONDS))
    val fullPageTitle    = s"$pageTitle - ${messages("label.your_personal_tax_account_gov_uk")}"
    val attorney         = Try(request.asInstanceOf[UserRequest[_]]) match {
      case Failure(_: java.lang.ClassCastException) => None
      case Success(value)                           => value.trustedHelper
      case Failure(exception)                       => throw exception
    }

    if (scaWrapperToggle.isEnabled) {
      logger.debug(s"SCA Wrapper layout used for request `${request.uri}``")
      wrapperService.layout(
        content = contentBlock,
        pageTitle = Some(fullPageTitle),
        serviceNameKey = Some(messages("label.your_personal_tax_account")),
        serviceNameUrl = None,
        sidebarContent = sidebarContent,
        signoutUrl = controllers.routes.ApplicationController
          .signout(Some(RedirectUrl(appConfig.getFeedbackSurveyUrl(appConfig.defaultOrigin))), None)
          .url,
        //timeOutUrl: Option[String] = appConfig.timeOutUrl,
        //keepAliveUrl: String = appConfig.keepAliveUrl,
        showBackLinkJS = showBackLink,
        //backLinkUrl: Option[String] = None,
        //showSignOutInHeader: Boolean = false,
        scripts = Seq(additionalScripts(scripts)),
        styleSheets = Seq(headBlock(stylesheets)),
        bannerConfig =
          if (showChildBenefitBanner)
            BannerConfig(true, false, true, false)
          else
            BannerConfig(false, false, true, true),
        optTrustedHelper = attorney,
        fullWidth = fullWidth,
        //hideMenuBar: Boolean = false,
        disableSessionExpired = disableSessionExpired
      )(messages, HeaderCarrierConverter.fromRequest(request), request)
    } else {
      logger.debug(s"Old layout used for request `${request.uri}``")
      oldLayout(
        pageTitle,
        serviceName,
        sidebarContent,
        showBackLink,
        backLinkID,
        backLinkUrl,
        disableSessionExpired,
        fullWidth,
        stylesheets,
        scripts,
        accountHome,
        messagesActive,
        yourProfileActive,
        hideAccountMenu,
        showChildBenefitBanner,
        showUserResearchBanner
      )(
        contentBlock
      )(request, messages)
    }
  }
}
