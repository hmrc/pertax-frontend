/*
 * Copyright 2026 HM Revenue & Customs
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
import connectors.FandFConnector
import models.AttorneyRelationshipResponse
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class FandFService @Inject() (fandfConnector: FandFConnector)(implicit ec: ExecutionContext) {
  def isAnyFandFRelationships(nino: Nino)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Boolean] =
    fandfConnector.getFandFAccountDetails(nino).map { response =>
      val relationships = response.json.as[Map[String, List[AttorneyRelationshipResponse]]]
      relationships.values.flatten.nonEmpty
    }
}
