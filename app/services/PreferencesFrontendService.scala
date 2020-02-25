/*
 * Copyright 2020 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import com.google.inject.{Inject, Singleton}
import metrics.HasMetrics
import models.{ActivatePaperlessActivatedResponse, ActivatePaperlessNotAllowedResponse, ActivatePaperlessRequiresUserActionResponse, ActivatePaperlessResponse}
import play.api.Mode.Mode
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.SessionCookieCrypto
import uk.gov.hmrc.play.partials.HeaderCarrierForPartialsConverter
import util.Tools

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreferencesFrontendService @Inject()(
  environment: Environment,
  runModeConfiguration: Configuration,
  val simpleHttp: DefaultHttpClient,
  val messagesApi: MessagesApi,
  val metrics: Metrics,
  val configDecorator: ConfigDecorator,
  val sessionCookieCrypto: SessionCookieCrypto,
  val tools: Tools,
  servicesConfig: ServicesConfig)(implicit ec: ExecutionContext)
    extends HeaderCarrierForPartialsConverter with HasMetrics with I18nSupport {

  val mode: Mode = environment.mode
  val preferencesFrontendUrl = servicesConfig.baseUrl("preferences-frontend")

  override def crypto: String => String = cookie => sessionCookieCrypto.crypto.encrypt(PlainText(cookie)).value

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
        } recover {
          case e =>
            timer.completeTimerAndIncrementFailedCounter()
            Logger.warn("Error getting paperless preference record from preferences-frontend-service", e)
            ActivatePaperlessNotAllowedResponse
        }
      }

    if (request.isGovernmentGateway) {
      activatePaperless
    } else {
      Future.successful(ActivatePaperlessNotAllowedResponse)
    }
  }

}
