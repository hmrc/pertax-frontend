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
import com.google.inject.Inject
import play.api.Logging
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.partials.HeaderCarrierForPartialsConverter

import scala.concurrent.{ExecutionContext, Future}

class MessageFrontendConnector @Inject() (
  val httpClient: HttpClient,
  servicesConfig: ServicesConfig,
  headerCarrierForPartialsConverter: HeaderCarrierForPartialsConverter,
  httpClientResponse: HttpClientResponse
) extends Logging {

  lazy val messageFrontendUrl: String = servicesConfig.baseUrl("message-frontend")

  def getUnreadMessageCount()(implicit
    request: RequestHeader,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val url = messageFrontendUrl + "/messages/count?read=No"

    implicit val hc = headerCarrierForPartialsConverter.fromRequestWithEncryptedCookie(request)

    httpClientResponse
      .read(httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](url))
  }

}
