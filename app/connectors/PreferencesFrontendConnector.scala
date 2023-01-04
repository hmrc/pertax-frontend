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

package connectors

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import play.api.Logging
import play.api.http.Status.{BAD_GATEWAY, INTERNAL_SERVER_ERROR, PRECONDITION_FAILED}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HttpReads.{is4xx, is5xx, upstreamResponseMessage}
import uk.gov.hmrc.http.{HttpClient, HttpReads, HttpReadsEither, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.partials.HeaderCarrierForPartialsConverter
import util.Tools
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreferencesFrontendConnector @Inject() (
  httpClient: HttpClient,
  val messagesApi: MessagesApi,
  val configDecorator: ConfigDecorator,
  val tools: Tools,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
)(implicit ec: ExecutionContext)
    extends HeaderCarrierForPartialsConverter
    with PreferencesCustomError
    with I18nSupport
    with Logging {

  val preferencesFrontendUrl = servicesConfig.baseUrl("preferences-frontend")
  val url                    = preferencesFrontendUrl

  def getPaperlessPreference()(implicit
    request: UserRequest[_]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {

    def absoluteUrl = configDecorator.pertaxFrontendHost + request.uri

    val url =
      s"$preferencesFrontendUrl/paperless/activate?returnUrl=${tools.encryptAndEncode(absoluteUrl)}&returnLinkText=${tools
        .encryptAndEncode(Messages("label.continue"))}" //TODO remove ref to Messages

    httpClientResponse
      .read(
        httpClient.PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](url, Json.obj("active" -> true))
      )
  }
}

trait PreferencesCustomError extends HttpReadsEither {
  private def handleResponseEither(response: HttpResponse): Either[UpstreamErrorResponse, HttpResponse] =
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

  override implicit def readEitherOf[A: HttpReads]: HttpReads[Either[UpstreamErrorResponse, A]] =
    HttpReads.ask.flatMap { case (_, _, response) =>
      handleResponseEither(response) match {
        case Left(err) =>
          HttpReads.pure(Left(err))
        case Right(_)  =>
          HttpReads[A].map(Right.apply)
      }
    }
}
