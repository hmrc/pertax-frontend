/*
 * Copyright 2025 HM Revenue & Customs
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

import config.ConfigDecorator
import models.sa.{SsttpRedirectPost, SsttpResponse}
import play.api.Logging
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.HttpErrorFunctions.is2xx
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class SsttpConnector @Inject() (val httpClientV2: HttpClientV2, appConfig: ConfigDecorator) extends Logging {

  def startPtaJourney()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[SsttpResponse]] = {

    val url = appConfig.ssttpPtaStartUrl
    logger.info(s"[SsttpConnector][startPtaJourney] post url is : $url")

    val postBody = SsttpRedirectPost("/personal-account", "/personal-account")

    httpClientV2
      .post(url"$url")
      .withBody(Json.toJson(postBody))
      .execute[HttpResponse]
      .map(parseSsttpResponse(url, _))
      .recover { case ex =>
        logger.error(s"[SsttpConnector][startPtaJourney] HTTP call to $url failed", ex)
        None
      }
  }

  private def parseSsttpResponse(url: String, response: HttpResponse): Option[SsttpResponse] =
    response.status match {
      case status if is2xx(status) =>
        Try(response.json.as[SsttpResponse](SsttpResponse.reads)) match {
          case Success(resp) => Some(resp)
          case Failure(ex)   =>
            logger.error("[SsttpConnector][parseSsttpResponse] Could not parse JSON", ex)
            None
        }
      case unexpectedStatus        =>
        logger.error(
          s"[SsttpConnector][parseSsttpResponse] Unexpected status $unexpectedStatus from $url, body: ${response.body}"
        )
        None
    }

}
