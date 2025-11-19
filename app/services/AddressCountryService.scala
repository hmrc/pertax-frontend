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

  def isCrossBorderScotland(
    currentAddressLines: Seq[String],
    currentPostcode: Option[String],
    newInternationalChoice: Option[InternationalAddressChoiceDto]
  )(implicit hc: HeaderCarrier): Future[Boolean] = {

    val newCountryNorm = normalization.normCountryFromChoice(newInternationalChoice)

    // If the new country is not set / not meaningful, we don't treat it as cross-border.
    if (newCountryNorm.isEmpty) {
      Future.successful(false)
    } else {
      currentPostcode.filter(_.trim.nonEmpty) match {
        case Some(pc) =>
          connector
            .lookup(pc, filter = None)
            .value
            .flatMap {
              case Right(recordSet) if recordSet.addresses.nonEmpty =>
                val addresses = recordSet.addresses

                def normLines(lines: Seq[String]): Seq[String] =
                  lines
                    .map(_.trim.toUpperCase.replaceAll("\\s+", " "))
                    .filter(_.nonEmpty)

                addresses match {

                  // Exactly one address for this postcode -> use its country
                  case single :: Nil =>
                    val oldCountryName = single.address.country.name
                    val oldCountryNorm = normalization.normCountry(Some(oldCountryName))
                    Future.successful(normalization.isCrossBorderScotland(oldCountryNorm, newCountryNorm))

                  // Multiple addresses -> try to match on address lines + postcode
                  case many =>
                    val currentNormLines = normLines(currentAddressLines)

                    val maybeMatch = many.find { rec =>
                      val recLinesNorm = normLines(rec.address.lines)
                      val samePostcode =
                        normalization.samePostcode(Some(rec.address.postcode), currentPostcode)

                      samePostcode && recLinesNorm == currentNormLines
                    }

                    maybeMatch match {
                      case Some(matched) =>
                        val oldCountryName = matched.address.country.name
                        val oldCountryNorm = normalization.normCountry(Some(oldCountryName))
                        Future.successful(normalization.isCrossBorderScotland(oldCountryNorm, newCountryNorm))

                      case None =>
                        logger.warn(
                          s"[AddressCountryService] Multiple addresses for postcode $pc but none matched by address lines; defaulting to cross-border=true"
                        )
                        Future.successful(true)
                    }
                }

              // Lookup succeeded but no addresses for this postcode
              case Right(_) =>
                logger.warn(
                  s"[AddressCountryService] Address lookup returned no addresses for postcode $pc; defaulting to cross-border=true"
                )
                Future.successful(true)

              // Lookup failed (error from connector)
              case Left(err) =>
                logger.warn(
                  s"[AddressCountryService] Address lookup failed for postcode $pc, defaulting to cross-border=true. Error: $err"
                )
                Future.successful(true)
            }

        // No usable current postcode -> safest is to treat as cross-border
        case None =>
          logger.warn(
            "[AddressCountryService] Current postcode is empty or missing; defaulting to cross-border=true"
          )
          Future.successful(true)
      }
    }
  }
}
