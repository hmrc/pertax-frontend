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
import cats.implicits._
import com.google.inject.Inject
import config.ConfigDecorator
import models.{SeissModel, SeissRequest}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SeissConnector @Inject() (
  httpClient: HttpClientV2,
  httpClientResponse: HttpClientResponse,
  implicit val ec: ExecutionContext,
  configDecorator: ConfigDecorator
) {

  def getClaims(utr: String)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, List[SeissModel]] = {
    val seissRequest = SeissRequest(utr)

    val url = s"${configDecorator.seissUrl}/self-employed-income-support/get-claims"

    val request = httpClient
      .post(url"$url")
      .withBody(seissRequest)

    httpClientResponse
      .read(request.execute[Either[UpstreamErrorResponse, HttpResponse]])
      .map(_.json.as[List[SeissModel]])
  }
}
