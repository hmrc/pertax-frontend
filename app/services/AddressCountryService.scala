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
import models.addresslookup.RecordSet
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddressCountryService @Inject() (
  connector: AddressLookupConnector,
  normalization: NormalizationUtils
)(implicit ec: ExecutionContext)
    extends Logging {

  def deriveCountryForPostcode(
    postcodeOpt: Option[String]
  )(implicit hc: HeaderCarrier): Future[Option[String]] =
    postcodeOpt.filter(_.trim.nonEmpty) match {
      case None =>
        logger.warn("[AddressCountryService] No postcode provided; cannot derive country")
        Future.successful(None)

      case Some(postcode) =>
        connector
          .lookup(postcode, filter = None)
          .value
          .map {
            case Right(recordSet: RecordSet) if recordSet.addresses.nonEmpty =>

              val normalisedCountries: Set[String] =
                recordSet.addresses
                  .flatMap { record =>
                    record.address.subdivision
                      .map(_.name)
                      .orElse(Some(record.address.country.name))
                  }
                  .map(name => normalization.normalizeCountryName(Some(name)))
                  .filter(_.nonEmpty)
                  .toSet

              normalisedCountries.toList match {
                case singleCountry :: Nil =>
                  Some(singleCountry)

                case _ =>
                  logger.warn(
                    s"[AddressCountryService] Multiple countries for postcode $postcode ($normalisedCountries); returning None"
                  )
                  None
              }

            case Right(_) =>
              logger.warn(
                s"[AddressCountryService] No addresses returned for postcode $postcode; returning None"
              )
              None

            case Left(error) =>
              logger.warn(
                s"[AddressCountryService] Address lookup failed for postcode $postcode; returning None. Error: $error"
              )
              None
          }
    }
}
