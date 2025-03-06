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

package services

import cats.data.EitherT
import com.google.inject.Inject
import connectors.CitizenDetailsConnector
import models.{Address, ETag, MatchingDetails, PersonDetails}
import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class CitizenDetailsService @Inject() (
  citizenDetailsConnector: CitizenDetailsConnector
) extends Logging {

  def personDetails(
    nino: Nino
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, PersonDetails] =
    citizenDetailsConnector.personDetails(nino).map(_.as[PersonDetails])

  def updateAddress(nino: Nino, etag: String, address: Address)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    citizenDetailsConnector.updateAddress(nino: Nino, etag: String, address: Address)

  def getMatchingDetails(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, MatchingDetails] =
    citizenDetailsConnector
      .getMatchingDetails(nino)
      .map { response =>
        MatchingDetails.fromJsonMatchingDetails(response.json)
      }

  def getEtag(
    nino: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, Option[ETag]] =
    citizenDetailsConnector.getEtag(nino).map(_.json.asOpt[ETag])
}
