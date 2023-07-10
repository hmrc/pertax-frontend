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
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.sca.services.WrapperService
import views.html.OldUnauthenticatedMainView
import views.html.components.{AdditionalJavascript, HeadBlock}

import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[UnauthenticatedMainViewImpl])
trait UnauthenticatedMainView {
  def apply(
    pageTitle: String,
    sidebarContent: Option[Html] = None,
    showBackLink: Boolean = false,
    disableSessionExpired: Boolean = false,
    fullWidth: Boolean = false
  )(contentBlock: Html)(implicit
    request: Request[_],
    messages: Messages
  ): HtmlFormat.Appendable
}

class UnauthenticatedMainViewImpl @Inject() (
  appConfig: ConfigDecorator,
  featureFlagService: FeatureFlagService,
  wrapperService: WrapperService,
  oldLayout: OldUnauthenticatedMainView,
  additionalScripts: AdditionalJavascript,
  headBlock: HeadBlock
) extends UnauthenticatedMainView
    with Logging {

  override def apply(
    pageTitle: String,
    sidebarContent: Option[Html] = None,
    showBackLink: Boolean = false,
    disableSessionExpired: Boolean = false,
    fullWidth: Boolean = false
  )(contentBlock: Html)(implicit request: Request[_], messages: Messages): HtmlFormat.Appendable = {
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
        serviceNameUrl = Some(appConfig.personalAccount),
        //sidebarContent: Option[Html] = None,
        signoutUrl = controllers.routes.ApplicationController
          .signout(Some(RedirectUrl(appConfig.getFeedbackSurveyUrl(appConfig.defaultOrigin))), None)
          .url,
        timeOutUrl = Some(controllers.routes.SessionManagementController.timeOut.url),
        keepAliveUrl = controllers.routes.SessionManagementController.keepAlive.url,
        showBackLinkJS = showBackLink,
        //backLinkUrl: Option[String] = None,
        showSignOutInHeader = !disableSessionExpired,
        scripts = Seq(additionalScripts(None)),
        styleSheets = Seq(headBlock(None)),
        //bannerConfig: BannerConfig = defaultBannerConfig,
        optTrustedHelper = attorney,
        fullWidth = fullWidth,
        hideMenuBar = true,
        disableSessionExpired = disableSessionExpired
      )(messages, HeaderCarrierConverter.fromRequest(request), request)
    } else {
      logger.debug(s"Old layout used for request `${request.uri}``")
      oldLayout(
        pageTitle,
        sidebarContent,
        showBackLink,
        disableSessionExpired,
        fullWidth
      )(
        contentBlock
      )(request, messages)
    }
  }
}
