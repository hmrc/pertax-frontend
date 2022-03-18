/*
 * Copyright 2022 HM Revenue & Customs
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

package models.dto

import controllers.controllershelpers.CountryHelper
import models.addresslookup.AddressRecord
import models.{Address, Country}
import org.joda.time.LocalDate
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import util.PertaxValidators._

case class AddressDto(
  line1: String,
  line2: String,
  line3: Option[String],
  line4: Option[String],
  line5: Option[String],
  postcode: Option[String],
  country: Option[String],
  propertyRefNo: Option[String]
) {
  def toAddress(`type`: String, startDate: LocalDate) = postcode match {
    case Some(postcode) =>
      println("4" * 100)
      Address(
        Some(line1),
        Some(line2),
        lineOrderCheck(LineThree),
        lineOrderCheck(LineFour),
        lineOrderCheck(LineFive),
        Some(formatMandatoryPostCode(postcode)),
        None,
        Some(startDate),
        None,
        Some(`type`),
        false
      )
    case None =>
      println("5" * 100)
      Address(Some(line1), Some(line2), line3, line4, line5, None, country, Some(startDate), None, Some(`type`), false)
  }

  def lineOrderCheck(lineNumber: LineNumberCheck) =
    lineNumber match {
      case LineThree =>
        line3 match {
          case Some(value)            => Some(value)
          case None if line4.nonEmpty => line4
          case None if line5.nonEmpty => line5
          case _                      => None
        }
      case LineFour =>
        line4 match {
          case Some(_) if line3.isEmpty && line5.nonEmpty => line5
          case Some(_) if line3.isEmpty                   => None
          case None if line3.nonEmpty && line5.nonEmpty   => line5
          case Some(value)                                => Some(value)
          case _                                          => None
        }
      case LineFive =>
        println("^" * 100)
        line5 match {
          case Some(_) if line3.isEmpty || line4.isEmpty => None
          case Some(value)                               => Some(value)
          case _                                         => None
        }
    }

  def toList: Seq[String] = {
    println("6" * 100)

    Seq(Some(line1), Some(line2), line3, line4, line5, postcode).flatten
  }
  def toListWithCountry: Seq[String] = {
    println("7" * 100)
    Seq(Some(line1), Some(line2), line3, line4, line5, country).flatten
  }
  def formatMandatoryPostCode(postCode: String): String = {
    println("8" * 100)
    val trimmedPostcode = postCode.replaceAll(" ", "").toUpperCase()
    val postCodeSplit = trimmedPostcode splitAt (trimmedPostcode.length - 3)
    postCodeSplit._1 + " " + postCodeSplit._2
  }
}

object AddressDto extends CountryHelper {

  implicit val formats = Json.format[AddressDto]

  def fromAddressRecord(addressRecord: AddressRecord): AddressDto = {

    println("2" * 100)

    val address = addressRecord.address
    val List(line1, line2, line3, line4, line5) =
      (address.lines.map(s => Option(s).filter(_.trim.nonEmpty)) ++ Seq(address.town)).padTo(5, None)

    AddressDto(
      line1.getOrElse(""),
      line2.getOrElse(""),
      Some(line3.getOrElse(line4.getOrElse(line5.getOrElse("")))),
      Some(line4.getOrElse(line5.getOrElse(""))),
      line5,
      Some(address.postcode),
      Some(address.country.toString),
      Some(addressRecord.id)
    )
  }

  val ukForm = Form(
    mapping(
      "line1" -> text
        .verifying("error.line1_required", _.nonEmpty)
        .verifying("error.line1_contains_more_than_35_characters", _.size <= 35)
        .verifying("error.line1_invalid_characters", e => validateAddressLineCharacters(Some(e))),
      "line2" -> text
        .verifying("error.line2_required", _.nonEmpty)
        .verifying("error.line2_contains_more_than_35_characters", _.size <= 35)
        .verifying("error.line2_invalid_characters", e => validateAddressLineCharacters(Some(e))),
      "line3" -> optional(text)
        .verifying("error.line3_contains_more_than_35_characters", _.size <= 35)
        .verifying("error.line3_invalid_characters", e => validateAddressLineCharacters(e)),
      "line4" -> optional(text)
        .verifying("error.line4_contains_more_than_35_characters", _.size <= 35)
        .verifying("error.line4_invalid_characters", e => validateAddressLineCharacters(e)),
      "line5" -> optional(text)
        .verifying("error.line5_contains_more_than_35_characters", _.size <= 35)
        .verifying("error.line5_invalid_characters", e => validateAddressLineCharacters(e)),
      "postcode" -> optional(text)
        .verifying(
          "error.enter_a_valid_uk_postcode",
          e =>
            e match {
              case Some(PostcodeRegex(_*)) => true
              case _                       => false
            }
        ),
      "country"       -> optional(text),
      "propertyRefNo" -> optional(nonEmptyText)
    )(AddressDto.apply)(AddressDto.unapply)
  )

  val internationalForm = Form(
    mapping(
      "line1" -> text
        .verifying("error.line1_required", _.nonEmpty)
        .verifying("error.line1_contains_more_than_35_characters", _.size <= 35)
        .verifying("error.line1_invalid_characters", e => validateAddressLineCharacters(Some(e))),
      "line2" -> text
        .verifying("error.line2_required", _.nonEmpty)
        .verifying("error.line2_contains_more_than_35_characters", _.size <= 35)
        .verifying("error.line2_invalid_characters", e => validateAddressLineCharacters(Some(e))),
      "line3" -> optional(text)
        .verifying("error.line3_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line3_invalid_characters", e => validateAddressLineCharacters(e)),
      "line4" -> optional(text)
        .verifying("error.line4_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line4_invalid_characters", e => validateAddressLineCharacters(e)),
      "line5" -> optional(text)
        .verifying("error.line5_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line5_invalid_characters", e => validateAddressLineCharacters(e)),
      "postcode" -> optional(text),
      "country" -> optional(text)
        .verifying("error.country_required", (e => countries.contains(Country(e.getOrElse(""))) && (e.isDefined))),
      "propertyRefNo" -> optional(nonEmptyText)
    )(AddressDto.apply)(AddressDto.unapply)
  )
}
