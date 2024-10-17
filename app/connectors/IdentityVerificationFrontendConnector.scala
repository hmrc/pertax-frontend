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
import play.api.Logging
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IdentityVerificationFrontendConnector @Inject() (
  val httpClient: HttpClientV2,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
) extends Logging {

  private lazy val identityVerificationFrontendUrl: String = servicesConfig.baseUrl("identity-verification-frontend")

  def getIVJourneyStatus(
    journeyId: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val url = s"$identityVerificationFrontendUrl/mdtp/journey/journeyId/$journeyId"

    httpClientResponse
      .read(
        httpClient
          .get(url"$url")
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
  }
}
