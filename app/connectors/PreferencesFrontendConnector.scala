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
import models.PaperlessMessagesStatus
import play.api.Logging
import play.api.http.Status.PRECONDITION_FAILED
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSRequest
import uk.gov.hmrc.http.{HttpReads, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.partials.HeaderCarrierForPartialsConverter
import util.Tools
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreferencesFrontendConnector @Inject() (
  httpClientV2: HttpClientV2,
  val messagesApi: MessagesApi,
  val configDecorator: ConfigDecorator,
  val tools: Tools,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
)(implicit ec: ExecutionContext)
    extends HeaderCarrierForPartialsConverter
    with I18nSupport
    with Logging {

  private val preferencesFrontendUrl: String = servicesConfig.baseUrl("preferences-frontend")
  val url: String                            = preferencesFrontendUrl

  def getPaperlessPreference()(implicit
    request: UserRequest[_]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {

    def newReadEitherOf[A: HttpReads]: HttpReads[Either[UpstreamErrorResponse, A]] =
      HttpReads.ask.flatMap { case (_, _, response) =>
        response.status match {
          case PRECONDITION_FAILED => HttpReads[A].map(Right.apply)
          case _                   => readEitherOf
        }
      }

    def absoluteUrl = configDecorator.pertaxFrontendHost + request.uri

    val url =
      s"$preferencesFrontendUrl/paperless/activate?returnUrl=${tools.encryptAndEncode(absoluteUrl)}&returnLinkText=${tools
        .encryptAndEncode(Messages("label.continue"))}" //TODO remove ref to Messages

    val body: JsObject = Json.obj("active" -> true)
    httpClientResponse
      .read(
        httpClientV2
          .put(url"$url")
          .withBody(body)
          .transform { request =>
            filterInvalidHeaders(request)
          }
          .execute[Either[UpstreamErrorResponse, HttpResponse]](newReadEitherOf(readRaw), ec)
      )
  }

  def getPaperlessStatus(url: String, returnMessage: String)(implicit
    request: UserRequest[_]
  ): EitherT[Future, UpstreamErrorResponse, PaperlessMessagesStatus] = {

    def absoluteUrl = configDecorator.pertaxFrontendHost + url
    val fullUrl     =
      url"$preferencesFrontendUrl/paperless/status?returnUrl=${tools.encryptOnly(absoluteUrl)}&returnLinkText=${tools
        .encryptOnly(returnMessage)}"
    httpClientResponse
      .read(
        httpClientV2
          .get(fullUrl)
          .transform { request =>
            filterInvalidHeaders(request).withRequestTimeout(configDecorator.preferenceFrontendTimeoutInSec.seconds)
          }
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map(_.json.as[PaperlessMessagesStatus])
  }

  private def filterInvalidHeaders(request: WSRequest): WSRequest = {
    val invalidChars                              = Seq(" ", "£")
    val filteredHeaders: Map[String, Seq[String]] =
      request.headers.filter(header => header._1.contains(invalidChars) || header._2.exists(_.contains(invalidChars)))
    if (filteredHeaders.nonEmpty) {
      val ex = new RuntimeException("Invalid characters in header \n" + filteredHeaders)
      logger.error(ex.getMessage, ex)
    }
    request
  }
}
