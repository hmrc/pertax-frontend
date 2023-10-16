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
import play.api.Logging
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsConnector @Inject() (
  val http: HttpClient,
  configDecorator: ConfigDecorator,
  httpClientResponse: HttpClientResponse
)(implicit ec: ExecutionContext)
    extends Logging {

  lazy val taxCreditsUrl: String = configDecorator.tcsBrokerHost

  def checkForTaxCredits(
    nino: Nino
  )(implicit headerCarrier: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    httpClientResponse.read(
      http.GET[Either[UpstreamErrorResponse, HttpResponse]](s"$taxCreditsUrl/tcs/$nino/dashboard-data")
    )
}
