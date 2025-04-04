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

package util

import cats.implicits.catsStdInstancesForFuture
import com.google.inject.Inject
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models._
import models.admin.AlertBannerPaperlessStatusToggle
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import play.twirl.api.Html
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import views.html.components.alertBanner.paperlessStatus.{bouncedEmail, unverifiedEmail}

import scala.concurrent.{ExecutionContext, Future}

class AlertBannerHelper @Inject() (
  preferencesFrontendConnector: PreferencesFrontendConnector,
  featureFlagService: FeatureFlagService,
  bouncedEmailView: bouncedEmail,
  unverifiedEmailView: unverifiedEmail
) {

  def getContent(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext,
    messages: Messages
  ): Future[List[Html]] = {
    val contentFutures = List(
      getPaperlessStatusBannerContent
    )
    Future.sequence(contentFutures).map(_.flatten)
  }

  private def getPaperlessStatusBannerContent(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext,
    messages: Messages
  ): Future[Option[Html]] =
    featureFlagService.get(AlertBannerPaperlessStatusToggle).flatMap {
      case toggle if toggle.isEnabled =>
        preferencesFrontendConnector
          .getPaperlessStatus(request.uri, "")
          .fold(
            _ => None,
            {
              case PaperlessStatusBounced(link)    => Some(bouncedEmailView(link))
              case PaperlessStatusUnverified(link) => Some(unverifiedEmailView(link))
              case _                               => None
            }
          )
      case _                          =>
        Future.successful(None)
    }
}
