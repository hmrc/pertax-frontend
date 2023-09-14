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

import com.google.inject.Inject
import config.ConfigDecorator
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models._
import models.admin.AlertBannerToggle
import play.api.mvc.AnyContent
import services.admin.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

class AlertBannerHelper @Inject() (
  preferencesFrontendConnector: PreferencesFrontendConnector,
  featureFlagService: FeatureFlagService,
  configDecorator: ConfigDecorator,
  tools: Tools
) {

  def alertBannerStatus()(implicit
    request: UserRequest[AnyContent],
    ec: ExecutionContext
  ): Future[Option[PaperlessMessages]] =
    featureFlagService.get(AlertBannerToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        preferencesFrontendConnector
          .getPaperlessStatus(request.uri, "")
          .fold(_ => None, message => Some(message))
          .map {
            case Some(paperlessStatus: PaperlessStatusBounced)    =>
              Some(paperlessStatus)
            case Some(paperlessStatus: PaperlessStatusUnverified) =>
              Some(paperlessStatus)
            case _                                                =>
              None
          }
      } else {
        Future.successful(None)
      }
    }

  def alertBannerUrl(
    verifyOrBounced: Option[PaperlessMessages]
  )(implicit request: UserRequest[_], ec: ExecutionContext): Future[Option[String]] = {
    val absoluteUrl = configDecorator.pertaxFrontendHost + request.uri

    featureFlagService.get(AlertBannerToggle).map { toggle =>
      if (toggle.isEnabled) {
        verifyOrBounced match {
          case Some(paperlessStatus: PaperlessStatusBounced)    =>
            Some(
              configDecorator.preferencedBouncedEmailLink(
                tools.encryptAndEncode(absoluteUrl),
                tools.encryptAndEncode(paperlessStatus.link)
              )
            )
          case Some(paperlessStatus: PaperlessStatusUnverified) =>
            Some(
              configDecorator.preferencedReVerifyEmailLink(
                tools.encryptAndEncode(absoluteUrl),
                tools.encryptAndEncode(paperlessStatus.link)
              )
            )
          case _                                                => None
        }
      } else {
        None
      }
    }
  }
}
