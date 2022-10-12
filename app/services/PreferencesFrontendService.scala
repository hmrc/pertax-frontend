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

import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import metrics.HasMetrics
import models.{ActivatePaperlessActivatedResponse, ActivatePaperlessNotAllowedResponse, ActivatePaperlessRequiresUserActionResponse, ActivatePaperlessResponse}
import play.api.Logging
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.partials.HeaderCarrierForPartialsConverter
import util.Tools

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreferencesFrontendService @Inject() (
  val simpleHttp: DefaultHttpClient,
  val messagesApi: MessagesApi,
  val metrics: Metrics,
  val configDecorator: ConfigDecorator,
  val tools: Tools,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext)
    extends HeaderCarrierForPartialsConverter
    with HasMetrics
    with I18nSupport
    with Logging {

  val preferencesFrontendUrl = servicesConfig.baseUrl("preferences-frontend")

  def getPaperlessPreference()(implicit request: UserRequest[_]): Future[ActivatePaperlessResponse] = {

    def absoluteUrl = configDecorator.pertaxFrontendHost + request.uri

    def activatePaperless: Future[ActivatePaperlessResponse] =
      withMetricsTimer("get-activate-paperless") { timer =>
        val url =
          s"$preferencesFrontendUrl/paperless/activate?returnUrl=${tools.encryptAndEncode(absoluteUrl)}&returnLinkText=${tools
            .encryptAndEncode(Messages("label.continue"))}" //TODO remove ref to Messages
        simpleHttp.PUT[JsObject, ActivatePaperlessResponse](url, Json.obj("active" -> true)) map {

          case ActivatePaperlessActivatedResponse =>
            timer.completeTimerAndIncrementSuccessCounter()
            ActivatePaperlessActivatedResponse

          case response: ActivatePaperlessRequiresUserActionResponse =>
            timer.completeTimerAndIncrementSuccessCounter()
            response

          case ActivatePaperlessNotAllowedResponse =>
            timer.completeTimerAndIncrementFailedCounter()
            ActivatePaperlessNotAllowedResponse
        } recover { case e =>
          timer.completeTimerAndIncrementFailedCounter()
          logger.warn("Error getting paperless preference record from preferences-frontend-service", e)
          ActivatePaperlessNotAllowedResponse
        }
      }

    activatePaperless
  }

}
