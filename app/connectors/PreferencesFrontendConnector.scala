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

package connectors

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import metrics.HasMetrics
import play.api.Logging
import play.api.http.Status.PRECONDITION_FAILED
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.partials.HeaderCarrierForPartialsConverter
import util.Tools

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreferencesFrontendConnector @Inject() (
  httpClient: HttpClient,
  val messagesApi: MessagesApi,
  val metrics: Metrics,
  val configDecorator: ConfigDecorator,
  val tools: Tools,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
)(implicit ec: ExecutionContext)
    extends HeaderCarrierForPartialsConverter
    with CustomError
    with HasMetrics
    with I18nSupport
    with Logging {

  val preferencesFrontendUrl = servicesConfig.baseUrl("preferences-frontend")

  def getPaperlessPreference()(implicit
    request: UserRequest[_]
  ): EitherT[Future, UpstreamErrorResponse, Option[String]] = {

    def absoluteUrl = configDecorator.pertaxFrontendHost + request.uri

    withMetricsTimer("get-activate-paperless") { timer =>
      val url =
        s"$preferencesFrontendUrl/paperless/activate?returnUrl=${tools.encryptAndEncode(absoluteUrl)}&returnLinkText=${tools
          .encryptAndEncode(Messages("label.continue"))}" //TODO remove ref to Messages

      httpClientResponse
        .read(
          httpClient.PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](url, Json.obj("active" -> true))
        )
        .transform {
          case Left(response) if response.statusCode == PRECONDITION_FAILED =>
            timer.completeTimerAndIncrementSuccessCounter()
            val redirectUrl = (Json.parse(response.message) \ "redirectUserTo").asOpt[String]
            Right(redirectUrl)
          case Right(_)                                                     =>
            timer.completeTimerAndIncrementSuccessCounter()
            Right(None)
          case Left(error)                                                  =>
            timer.completeTimerAndIncrementFailedCounter()
            Left(error)
        }
    }
  }
}
