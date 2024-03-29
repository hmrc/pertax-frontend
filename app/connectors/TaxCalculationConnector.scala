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
import models.TaxYearReconciliation
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxCalculationConnector @Inject() (
  val httpClientV2: HttpClientV2,
  servicesConfig: ServicesConfig,
  configDecorator: ConfigDecorator,
  httpClientResponse: HttpClientResponse
)(implicit ec: ExecutionContext) {

  private lazy val taxCalcUrl = servicesConfig.baseUrl("taxcalc")

  def getTaxYearReconciliations(
    nino: Nino
  )(implicit headerCarrier: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, List[TaxYearReconciliation]] = {
    val url         = s"$taxCalcUrl/taxcalc/$nino/reconciliations"
    val apiResponse = httpClientV2
      .get(url"$url")
      .transform(_.withRequestTimeout(configDecorator.taxCalcTimeoutInMilliseconds.milliseconds))
      .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
    httpClientResponse.read(apiResponse).map(_.json.as[List[TaxYearReconciliation]])
  }
}
