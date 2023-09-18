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
import config.ConfigDecorator
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models._
import models.admin.AlertBannerPaperlessStatusToggle
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import play.twirl.api.Html
import services.admin.FeatureFlagService
import views.html.components.alertBanner.paperlessStatus.{bouncedEmail, unverifiedEmail}
import scala.concurrent.{ExecutionContext, Future}

class AlertBannerHelper @Inject() (
  preferencesFrontendConnector: PreferencesFrontendConnector,
  featureFlagService: FeatureFlagService,
  configDecorator: ConfigDecorator,
  tools: Tools,
  bouncedEmailView: bouncedEmail,
  unverifiedEmailView: unverifiedEmail
) {

  def getContent(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext,
    messages: Messages
  ): Future[List[Html]] =
    for {
      paperlessContent <- getPaperlessStatusBannerContent
    } yield List(
      paperlessContent
    ).flatten

  def getPaperlessStatusBannerContent(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext,
    messages: Messages
  ): Future[Option[Html]] =
    featureFlagService.get(AlertBannerPaperlessStatusToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        val absoluteUrl = configDecorator.pertaxFrontendHost + request.uri
        preferencesFrontendConnector
          .getPaperlessStatus(request.uri, "")
          .fold(
            _ => None,
            {
              case paperlessStatus: PaperlessStatusBounced =>
                val bounceLink = configDecorator.preferencedBouncedEmailLink(
                  tools.encryptAndEncode(absoluteUrl),
                  tools.encryptAndEncode(paperlessStatus.link)
                )
                Some(bouncedEmailView(bounceLink))

              case paperlessStatus: PaperlessStatusUnverified =>
                val unverifiedLink = configDecorator.preferencedReVerifyEmailLink(
                  tools.encryptAndEncode(absoluteUrl),
                  tools.encryptAndEncode(paperlessStatus.link)
                )
                Some(unverifiedEmailView(unverifiedLink))
              case _                                          =>
                None
            }
          )
      } else {
        Future.successful(None)
      }
    }
}
