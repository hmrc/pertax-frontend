/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import connectors.{EnhancedPartialRetriever, PreferencesFrontendConnector}
import controllers.auth.requests.UserRequest
import models.{PaperlessMessages, PaperlessResponse, PaperlessStatus, PaperlessUrl}
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.partials.HtmlPartial
import util.Tools

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreferencesFrontendService @Inject() (
  tools: Tools,
  servicesConfig: ServicesConfig,
  preferencesFrontendConnector: PreferencesFrontendConnector
)(implicit executionContext: ExecutionContext) {

  val preferencesFrontendUrl = servicesConfig.baseUrl("preferences-frontend")

  def getPaperlessPreference(url: String, returnMessage: String)(implicit
    request: UserRequest[_]
  ): EitherT[Future, UpstreamErrorResponse, PaperlessMessages] =
    preferencesFrontendConnector.getPaperlessStatus(url, returnMessage).map {
      case PaperlessResponse(PaperlessStatus("NEW_CUSTOMER", _), _) =>
        PaperlessMessages(
          "label.paperless_new_response",
          "label.paperless_new_link",
          Some("label.paperless_new_hidden")
        )

      case PaperlessResponse(PaperlessStatus("BOUNCED_EMAIL", _), _) =>
        PaperlessMessages(
          "label.paperless_bounced_response",
          "label.paperless_bounced_link",
          Some("label.paperless_bounced_hidden")
        )

      case PaperlessResponse(PaperlessStatus("EMAIL_NOT_VERIFIED", _), _) =>
        PaperlessMessages(
          "label.paperless_unverified_response",
          "label.paperless_unverified_link",
          Some("label.paperless_unverified_hidden")
        )

      case PaperlessResponse(PaperlessStatus("RE_OPT_IN", _), _) =>
        PaperlessMessages("label.paperless_reopt_response", "label.paperless_reopt_link", None)

      case PaperlessResponse(PaperlessStatus("RE_OPT_IN_MODIFIED", _), _) =>
        PaperlessMessages("label.paperless_reopt_modified_response", "label.paperless_reopt_modified_link", None)

      case PaperlessResponse(PaperlessStatus("PAPER", _), _) =>
        PaperlessMessages(
          "label.paperless_opt_out_response",
          "label.paperless_opt_out_link",
          Some("label.paperless_opt_out_hidden")
        )

      case PaperlessResponse(PaperlessStatus("ALRIGHT", _), _) =>
        PaperlessMessages(
          "label.paperless_opt_in_response",
          "label.paperless_opt_in_link",
          Some("label.paperless_opt_in_hidden")
        )

      case PaperlessResponse(PaperlessStatus("NO_EMAIL", _), _) =>
        PaperlessMessages(
          "label.paperless_no_email_response",
          "label.paperless_no_email_link",
          Some("label.paperless_no_email_hidden")
        )

      case _ =>
        PaperlessMessages(
          "label.paperless_no_email_response",
          "label.paperless_no_email_link",
          Some("label.paperless_no_email_hidden")
        )
    }
}
