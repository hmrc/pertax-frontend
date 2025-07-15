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

package controllers.controllershelpers

import models.Address
import models.dto.AddressDto

object AddressJourneyAuditingHelper {

  def addressWasUnmodified(originalAddressDto: Option[AddressDto], addressDto: AddressDto): Boolean =
    originalAddressDto.exists { o =>
      o.postcode == addressDto.postcode &&
      o.line1 == addressDto.line1 &&
      o.line2 == addressDto.line2 &&
      o.line3 == addressDto.line3 &&
      o.line4 == addressDto.line4
    }

  def addressWasHeavilyModifiedOrManualEntry(originalAddressDto: Option[AddressDto], addressDto: AddressDto): Boolean =
    originalAddressDto.forall { o =>
      val lines         = List(addressDto.line1, addressDto.line2, addressDto.line3, addressDto.line4)
      val originalLines = List(o.line1, o.line2, o.line3, o.line4)
      val changeCount   = (lines zip originalLines).count(e => e._1 != e._2)

      o.postcode != addressDto.postcode ||
      (changeCount > 2)
    }

  private def addressDtoToAuditData(addressDto: AddressDto, prefix: String): Map[String, Option[String]] =
    Map(
      s"${prefix}Line1"    -> Some(addressDto.line1),
      s"${prefix}Line2"    -> addressDto.line2,
      s"${prefix}Line3"    -> addressDto.line3,
      s"${prefix}Line4"    -> addressDto.line4,
      s"${prefix}Postcode" -> addressDto.postcode,
      s"${prefix}Country"  -> addressDto.country,
      s"${prefix}UPRN"     -> addressDto.propertyRefNo
    ).foldLeft(List[(String, Option[String])]())((acc, cur) => cur._2.fold(acc)(_ => cur :: acc)).toMap

  def dataToAudit(
    addressDto: AddressDto,
    etag: String,
    addressType: String,
    originalAddressDto: Option[AddressDto],
    propertyRefNo: Option[String]
  ): Map[String, Option[String]] =
    originalAddressDto.fold(Map[String, Option[String]]())(original => addressDtoToAuditData(original, "original")) ++
      addressDtoToAuditData(addressDto, "submitted") ++
      Map(
        "submittedUPRN" -> propertyRefNo,
        "etag"          -> Some(etag),
        "addressType"   -> Some(addressType)
      )

  def auditForClosingPostalAddress(address: Address, etag: String, addressType: String): Map[String, Option[String]] =
    Map(
      "submittedLine1"    -> address.line1,
      "submittedLine2"    -> address.line2,
      "submittedLine3"    -> address.line3,
      "submittedLine4"    -> address.line4,
      "submittedPostcode" -> address.postcode,
      "submittedCountry"  -> address.country
    ).foldLeft(List[(String, Option[String])]())((acc, cur) => cur._2.fold(acc)(_ => cur :: acc)).toMap ++
      Map(
        "etag"        -> Some(etag),
        "addressType" -> Some(addressType)
      )

}
