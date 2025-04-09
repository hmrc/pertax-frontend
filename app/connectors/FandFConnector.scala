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
import com.google.inject.Inject
import config.ConfigDecorator
import play.api.Logging
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

class FandFConnector @Inject() (
  val httpClient: HttpClientV2,
  configDecorator: ConfigDecorator,
  httpClientResponse: HttpClientResponse
)(implicit val ec: ExecutionContext)
    extends Logging {

  def getDelegation()(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Option[TrustedHelper]] =
    httpClientResponse
      .read(
        httpClient
          .get(url"${configDecorator.fandfHost}/delegation/get")
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
      .map(_.json.asOpt[TrustedHelper](uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper.reads))
}
