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

package models.dto

import controllers.controllershelpers.CountryHelper
import models.addresslookup.AddressRecord
import models.{Address, Country}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{Json, OFormat}
import util.PertaxValidators._

import java.time.LocalDate
import scala.annotation.nowarn

case class AddressDto(
  line1: String,
  line2: String,
  line3: Option[String],
  line4: Option[String],
  line5: Option[String],
  postcode: Option[String],
  country: Option[String],
  propertyRefNo: Option[String]
) extends Dto {
  def toAddress(`type`: String, startDate: LocalDate): Address =
    postcode match {
      case Some(postcode) =>
        Address(
          Some(line1),
          Some(line2),
          line3,
          line4,
          line5,
          Some(formatMandatoryPostCode(postcode)),
          None,
          Some(startDate),
          None,
          Some(`type`),
          isRls = false
        )
      case None           =>
        Address(
          Some(line1),
          Some(line2),
          line3,
          line4,
          line5,
          None,
          country,
          Some(startDate),
          None,
          Some(`type`),
          isRls = false
        )
    }

  def toList: Seq[String] =
    Seq(Some(line1), Some(line2), line3, line4, line5, postcode).flatten

  def toListWithCountry: Seq[String] =
    Seq(Some(line1), Some(line2), line3, line4, line5, country).flatten

  def formatMandatoryPostCode(postCode: String): String = {
    val trimmedPostcode = postCode.replaceAll(" ", "").toUpperCase()
    val postCodeSplit   = trimmedPostcode splitAt (trimmedPostcode.length - 3)
    postCodeSplit._1 + " " + postCodeSplit._2
  }
}

object AddressDto extends CountryHelper {

  def unapply(obj: AddressDto): Some[
    (String, String, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String])
  ] = Some(
    (obj.line1, obj.line2, obj.line3, obj.line4, obj.line5, obj.postcode, obj.country, obj.propertyRefNo)
  )

  @nowarn("msg=match may not be exhaustive.")
  def apply(
    line1: String,
    line2: String,
    line3: Option[String],
    line4: Option[String],
    line5: Option[String],
    postcode: Option[String],
    country: Option[String],
    propertyRefNo: Option[String]
  ): AddressDto =
    List(line3, line4, line5).filter(op => op.exists(line => line.nonEmpty)).padTo(3, None) match {
      case List(newLine3, newLine4, newLine5) =>
        new AddressDto(line1, line2, newLine3, newLine4, newLine5, postcode, country, propertyRefNo)
    }

  implicit val formats: OFormat[AddressDto] = Json.format[AddressDto]

  @nowarn("msg=match may not be exhaustive.")
  def fromAddressRecord(addressRecord: AddressRecord): AddressDto = {
    val defaultPad = 5
    (addressRecord.address.lines.map(s => Option(s).filter(_.trim.nonEmpty)) ++ Seq(addressRecord.address.town))
      .padTo(defaultPad, None) match {
      case List(line1, line2, line3, line4, line5) =>
        AddressDto(
          line1.getOrElse(""),
          line2.getOrElse(""),
          line3,
          line4,
          line5,
          Some(addressRecord.address.postcode),
          Some(addressRecord.address.country.toString),
          Some(addressRecord.id)
        )
    }
  }

  val ukForm: Form[AddressDto] = Form(
    mapping(
      "line1"         -> text
        .verifying("error.line1_required", _.nonEmpty)
        .verifying("error.line1_contains_more_than_35_characters", _.length <= 35)
        .verifying("error.line1_invalid_characters", e => validateAddressLineCharacters(Some(e))),
      "line2"         -> text
        .verifying("error.line2_required", _.nonEmpty)
        .verifying("error.line2_contains_more_than_35_characters", _.length <= 35)
        .verifying("error.line2_invalid_characters", e => validateAddressLineCharacters(Some(e))),
      "line3"         -> optional(text)
        .verifying("error.line3_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line3_invalid_characters", e => validateAddressLineCharacters(e)),
      "line4"         -> optional(text)
        .verifying("error.line4_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line4_invalid_characters", e => validateAddressLineCharacters(e)),
      "line5"         -> optional(text)
        .verifying("error.line5_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line5_invalid_characters", e => validateAddressLineCharacters(e)),
      "postcode"      -> optional(text)
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

  val internationalForm: Form[AddressDto] = Form(
    mapping(
      "line1"         -> text
        .verifying("error.line1_required", _.nonEmpty)
        .verifying("error.line1_contains_more_than_35_characters", _.length <= 35)
        .verifying("error.line1_invalid_characters", e => validateAddressLineCharacters(Some(e))),
      "line2"         -> text
        .verifying("error.line2_required", _.nonEmpty)
        .verifying("error.line2_contains_more_than_35_characters", _.length <= 35)
        .verifying("error.line2_invalid_characters", e => validateAddressLineCharacters(Some(e))),
      "line3"         -> optional(text)
        .verifying("error.line3_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line3_invalid_characters", e => validateAddressLineCharacters(e)),
      "line4"         -> optional(text)
        .verifying("error.line4_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line4_invalid_characters", e => validateAddressLineCharacters(e)),
      "line5"         -> optional(text)
        .verifying("error.line5_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line5_invalid_characters", e => validateAddressLineCharacters(e)),
      "postcode"      -> optional(text),
      "country"       -> optional(text)
        .verifying("error.country_required", e => countries.contains(Country(e.getOrElse(""))) && e.isDefined),
      "propertyRefNo" -> optional(nonEmptyText)
    )(AddressDto.apply)(AddressDto.unapply)
  )
}
