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

import cats.data.EitherT
import config.ConfigDecorator
import models.sa.{SsttpRedirectPost, SsttpResponse}
import play.api.Logging
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SsttpConnector @Inject() (
  val httpClientV2: HttpClientV2,
  httpClientResponse: HttpClientResponse,
  appConfig: ConfigDecorator
) extends Logging {

  def startPtaJourney()(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, SsttpResponse] = {

    val url      = appConfig.ssttpPtaStartUrl
    val postBody = SsttpRedirectPost("/personal-account", "/personal-account")

    httpClientResponse
      .read(
        httpClientV2
          .post(url"$url")
          .withBody(Json.toJson(postBody))
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map(_.json.as[SsttpResponse])
  }
}
