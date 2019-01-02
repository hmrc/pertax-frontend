/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import metrics.HasMetrics
import models.PertaxUser
import play.api.Logger
import play.api.http.Status._
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}
import services.http.SimpleHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.filters.SessionCookieCryptoFilter
import uk.gov.hmrc.play.partials.HeaderCarrierForPartialsConverter

import scala.concurrent.Future

sealed trait ActivatePaperlessResponse
case object ActivatePaperlessActivatedResponse extends ActivatePaperlessResponse
case object ActivatePaperlessNotAllowedResponse extends ActivatePaperlessResponse
case class ActivatePaperlessRequiresUserActionResponse(redirectUrl: String) extends ActivatePaperlessResponse


@Singleton
class PreferencesFrontendService @Inject() (val simpleHttp: SimpleHttp, val messagesApi: MessagesApi, val metrics: Metrics, val configDecorator: ConfigDecorator) extends HeaderCarrierForPartialsConverter with ServicesConfig with HasMetrics with I18nSupport {

  val preferencesFrontendUrl = baseUrl("preferences-frontend")
  override def crypto = SessionCookieCryptoFilter.encrypt

  import util.Tools._

  def getPaperlessPreference(pertaxUser: PertaxUser)(implicit request: Request[AnyContent]): Future[ActivatePaperlessResponse] = {

    def absoluteUrl = configDecorator.pertaxFrontendHost + request.uri

    def activatePaperless : Future[ActivatePaperlessResponse] = {

      withMetricsTimer("get-activate-paperless") { t =>

        val url = s"$preferencesFrontendUrl/paperless/activate?returnUrl=${encryptAndEncode(absoluteUrl)}&returnLinkText=${encryptAndEncode(Messages("label.continue"))}" //TODO remove ref to Messages

        simpleHttp.put[JsObject, ActivatePaperlessResponse](url, Json.obj("active" -> true))(
          onComplete = {
            case r if r.status >= 200 && r.status < 300 =>
              t.completeTimerAndIncrementSuccessCounter()
              ActivatePaperlessActivatedResponse

            case r if r.status == PRECONDITION_FAILED =>
              t.completeTimerAndIncrementSuccessCounter()
              val redirectUrl = (r.json \ "redirectUserTo")
              Logger.warn("Precondition failed when getting paperless preference record from preferences-frontend-service")
              ActivatePaperlessRequiresUserActionResponse(redirectUrl.as[String])

            case r =>
              t.completeTimerAndIncrementFailedCounter()
              Logger.warn(s"Unexpected ${r.status} response getting paperless preference record from preferences-frontend-service")
              ActivatePaperlessNotAllowedResponse
          },
          onError = {
            case e =>
              t.completeTimerAndIncrementFailedCounter()
              Logger.warn("Error getting paperless preference record from preferences-frontend-service", e)
              ActivatePaperlessNotAllowedResponse
          }
        )
      }
    }

    if(pertaxUser.isGovernmentGateway) {
      activatePaperless
    } else {
      Future.successful(ActivatePaperlessNotAllowedResponse)
    }
  }

}
