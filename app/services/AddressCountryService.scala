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

package services

import connectors.AddressLookupConnector
import models.dto.InternationalAddressChoiceDto
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddressCountryService @Inject() (connector: AddressLookupConnector, normalization: NormalizationUtils)(implicit
  ec: ExecutionContext
) {

  def isCrossBorderScotland(
    currentPostcode: Option[String],
    newInternationalChoice: Option[InternationalAddressChoiceDto]
  )(implicit hc: HeaderCarrier): Future[Boolean] = {
    val newCountryNorm = normalization.normCountryFromChoice(newInternationalChoice)

    currentPostcode match {
      case Some(pc) if pc.trim.nonEmpty =>
        connector.lookup(pc, filter = None).value.flatMap {
          case Right(recordSet) if recordSet.addresses.nonEmpty =>
            val oldCountryName = recordSet.addresses.head.address.country.name
            val oldCountryNorm = normalization.normCountry(Option(oldCountryName))
            Future.successful(normalization.isCrossBorderScotland(oldCountryNorm, newCountryNorm))

          case _ =>
            Future.failed(new RuntimeException("Address lookup failed or returned no results"))
        }

      case _ =>
        Future.failed(new RuntimeException("Missing postcode for cross-border check"))
    }
  }
}
