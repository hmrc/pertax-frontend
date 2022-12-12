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
import models.PaperlessStatusResponse
import play.api.Logging
import play.api.http.Status.{BAD_GATEWAY, INTERNAL_SERVER_ERROR, PRECONDITION_FAILED}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HttpReads.{is4xx, is5xx, upstreamResponseMessage}
import uk.gov.hmrc.http.{HttpClient, HttpReads, HttpReadsEither, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.partials.HeaderCarrierForPartialsConverter
import util.Tools
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreferencesFrontendConnector @Inject() (
  httpClient: HttpClient,
  httpClientV2: HttpClientV2,
  val messagesApi: MessagesApi,
  val metrics: Metrics,
  val configDecorator: ConfigDecorator,
  val tools: Tools,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
)(implicit ec: ExecutionContext)
    extends HeaderCarrierForPartialsConverter
    with HasMetrics
    with I18nSupport
    with Logging {

  val preferencesFrontendUrl = servicesConfig.baseUrl("preferences-frontend")
  val url                    = preferencesFrontendUrl

  def getPaperlessPreference()(implicit
    request: UserRequest[_]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    def handleResponseEither(response: HttpResponse): Either[UpstreamErrorResponse, HttpResponse] =
      response.status match {

        case status if status != PRECONDITION_FAILED && is4xx(status) || is5xx(status) =>
          Left(
            UpstreamErrorResponse(
              message = upstreamResponseMessage("PUT", response.body, status, response.body),
              statusCode = status,
              reportAs = if (is4xx(status)) INTERNAL_SERVER_ERROR else BAD_GATEWAY,
              headers = response.headers
            )
          )
        case _                                                                         => Right(response)
      }

    def newReadEitherOf[A: HttpReads]: HttpReads[Either[UpstreamErrorResponse, A]] =
      HttpReads.ask.flatMap { case (_, _, response) =>
        handleResponseEither(response) match {
          case Left(err) =>
            HttpReads.pure(Left(err))
          case Right(_)  =>
            HttpReads[A].map(Right.apply)
        }
      }

    def absoluteUrl = configDecorator.pertaxFrontendHost + request.uri

    val url =
      s"$preferencesFrontendUrl/paperless/activate?returnUrl=${tools.encryptAndEncode(absoluteUrl)}&returnLinkText=${tools
        .encryptAndEncode(Messages("label.continue"))}" //TODO remove ref to Messages

    withMetricsTimer("get-activate-paperless") { timer =>
      httpClientResponse
        .read(
          httpClient.PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](url, Json.obj("active" -> true))(
            wts = implicitly,
            rds = newReadEitherOf,
            ec = implicitly,
            hc = implicitly
          )
        )
        .bimap(
          error => {
            timer.completeTimerAndIncrementFailedCounter()
            error
          },
          response => {
            timer.completeTimerAndIncrementSuccessCounter()
            response
          }
        )
    }
  }

  def getPaperlessStatus(url: String, returnMessage: String)(implicit
    request: UserRequest[_]
  ): EitherT[Future, UpstreamErrorResponse, PaperlessStatusResponse] = {

    def absoluteUrl = configDecorator.pertaxFrontendHost + url
    val fullUrl     =
      s"$preferencesFrontendUrl/paperless/status?returnUrl=${tools.encryptAndEncode(absoluteUrl)}&returnLinkText=${tools
        .encryptAndEncode(returnMessage)}"
    httpClientResponse
      .read(
        httpClientV2
          .get(url"$fullUrl")
          .transform(_.withRequestTimeout(5.seconds))
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map(_.json.as[PaperlessStatusResponse])
  }
}
