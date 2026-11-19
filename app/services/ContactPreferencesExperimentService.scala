/*
 * Copyright 2026 HM Revenue & Customs
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

package services

import com.google.inject.Inject
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models.PaperlessStatusOptOut
import play.api.Logging
import play.api.i18n.Messages
import viewmodels.PtapContactPreferencesExperiment

import scala.concurrent.{ExecutionContext, Future}

class ContactPreferencesExperimentService @Inject() (
  preferencesFrontendConnector: PreferencesFrontendConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  def get(implicit
    request: UserRequest[?],
    messages: Messages
  ): Future[Option[PtapContactPreferencesExperiment]] =
    preferencesFrontendConnector
      .getPaperlessStatus(request.uri, messages("ptap.contactPreferences.returnLinkText"))
      .value
      .map {
        case Right(PaperlessStatusOptOut(link)) => Some(PtapContactPreferencesExperiment(Some(link)))
        case _                                  => None
      }
      .recover { case _ =>
        logger.warn("Unable to retrieve contact preferences experiment data")
        None
      }
}
