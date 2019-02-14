/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers.helpers

import models.dto.AddressDto

object AddressJourneyAuditingHelper {

  def addressWasUnmodified(originalAddressDto: Option[AddressDto], addressDto: AddressDto): Boolean = {

        originalAddressDto.map { o =>
          o.postcode == addressDto.postcode &&
            o.line1 == addressDto.line1 &&
            o.line2 == addressDto.line2 &&
            o.line3 == addressDto.line3 &&
            o.line4 == addressDto.line4 &&
            o.line5 == addressDto.line5
        } getOrElse false
  }

  def addressWasHeavilyModifiedOrManualEntry(originalAddressDto: Option[AddressDto], addressDto: AddressDto): Boolean = {

    originalAddressDto.map {
        o =>
          //Count address line changes
        val lines = List (addressDto.line1, addressDto.line2, addressDto.line3, addressDto.line4)
        val originalLines = List (o.line1, o.line2, o.line3, o.line4)
        val changeCount = (lines zip originalLines).filter (e => e._1 != e._2).size

        o.postcode != addressDto.postcode ||
        (changeCount > 2)
    } getOrElse true
  }

  def addressDtoToAuditData(addressDto: AddressDto, prefix: String): Map[String, Option[String]] = {
    Map(
      s"${prefix}Line1"    -> Some(addressDto.line1),
      s"${prefix}Line2"    -> Some(addressDto.line2),
      s"${prefix}Line3"    -> addressDto.line3,
      s"${prefix}Line4"    -> addressDto.line4,
      s"${prefix}Line5"    -> addressDto.line5,
      s"${prefix}Postcode" -> addressDto.postcode,
      s"${prefix}Country"  -> addressDto.country,
      s"${prefix}UPRN"     -> addressDto.propertyRefNo
    ).foldLeft(List[(String,Option[String])]())( (acc, cur) => cur._2.fold(acc)( x => cur :: acc ) ).toMap
  }


  def dataToAudit(addressDto: AddressDto, etag: String, addressType: String, originalAddressDto: Option[AddressDto],
                    propertyRefNo: Option[String]): Map[String, Option[String]] = {
    originalAddressDto.fold(Map[String, Option[String]]())(original => addressDtoToAuditData(original, "original")) ++
      addressDtoToAuditData(addressDto, "submitted") ++
      Map(
        "submittedUPRN" -> propertyRefNo,
        "etag" -> Some(etag),
        "addressType" -> Some(addressType)
      )

  }
}
