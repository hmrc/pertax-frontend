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
import com.google.inject.Inject
import config.ConfigDecorator
import models.PertaxResponse
import play.api.Logging
import play.api.http.HeaderNames
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}

import scala.concurrent.{ExecutionContext, Future}

class PertaxConnector @Inject() (
  httpClient: HttpClientV2,
  httpClientResponse: HttpClientResponse,
  configDecorator: ConfigDecorator,
  headerCarrierForPartialsConverter: HeaderCarrierForPartialsConverter
) extends Logging {
  private val pertaxUrl = configDecorator.pertaxUrl

  def pertaxPostAuthorise(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, PertaxResponse] = {
    val url = s"$pertaxUrl/pertax/authorise"

    lazy val request = httpClient
      .post(url"$url")
      .setHeader(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")

    httpClientResponse
      .readLogUnauthorisedAsInfo(request.execute[Either[UpstreamErrorResponse, HttpResponse]])
      .map(_.json.as[PertaxResponse])
  }

  def loadPartial(url: String)(implicit request: RequestHeader, ec: ExecutionContext): Future[HtmlPartial] = {
    implicit val hc: HeaderCarrier = headerCarrierForPartialsConverter.fromRequestWithEncryptedCookie(request)

    val urlValue = s"$pertaxUrl$url"
    httpClient
      .get(url"$urlValue")
      .execute[HtmlPartial]
      .map {
        case partial: HtmlPartial.Success => partial
        case partial: HtmlPartial.Failure =>
          logger.error(s"Failed to load partial from $url, partial info: $partial, body: ${partial.body}")
          partial
      } recover { case e =>
      logger.error(s"Failed to load partial from $url", e)
      e match {
        case ex: HttpException =>
          HtmlPartial.Failure(Some(ex.responseCode))
        case _                 =>
          HtmlPartial.Failure(None)
      }
    }
  }
}
