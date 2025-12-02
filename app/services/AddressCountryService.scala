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
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddressCountryService @Inject() (connector: AddressLookupConnector)(implicit ec: ExecutionContext) {

  def deriveCountryForPostcode(postcodeOpt: Option[String])(implicit hc: HeaderCarrier): Future[Option[String]] =
    postcodeOpt.filter(_.trim.nonEmpty) match {
      case None =>
        Future.successful(None)

      case Some(postcode) =>
        connector
          .lookup(postcode, filter = None)
          .value
          .map(_.toOption.flatMap { recordSet =>
            val codes: List[String] =
              recordSet.addresses
                .flatMap(_.address.subdivision.flatMap(sub => Option(sub.code)))
                .distinct
                .toList

            codes match {
              case single :: Nil => Some(single)
              case _             => None
            }
          })
    }
}
