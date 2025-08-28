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
  line2: Option[String],
  line3: Option[String],
  line4OrTown: Option[String],
  line5OrCounty: Option[String],
  postcode: Option[String],
  country: Option[String],
  propertyRefNo: Option[String]
) extends Dto {
  def toAddress(`type`: String, startDate: LocalDate): Address = {
    val List(newLine2, newLine3, newline4OrTown, newline5OrCounty) =
      List(line2, line3, line4OrTown, line5OrCounty).flatten.map(Some(_)).padTo(4, None)
    postcode match {
      case Some(postcode) =>
        Address(
          Some(line1),
          newLine2,
          newLine3,
          newline4OrTown,
          newline5OrCounty,
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
          newLine2,
          newLine3,
          newline4OrTown,
          newline5OrCounty,
          None,
          country,
          Some(startDate),
          None,
          Some(`type`),
          isRls = false
        )
    }
  }

  def toList: Seq[String] =
    Seq(Some(line1), line2, line3, line4OrTown, line5OrCounty, postcode).flatten

  def toListWithCountry: Seq[String] =
    Seq(Some(line1), line2, line3, line4OrTown, line5OrCounty, country).flatten

  def formatMandatoryPostCode(postCode: String): String = {
    val trimmedPostcode = postCode.replaceAll(" ", "").toUpperCase()
    val postCodeSplit   = trimmedPostcode splitAt (trimmedPostcode.length - 3)
    postCodeSplit._1 + " " + postCodeSplit._2
  }
}

object AddressDto extends CountryHelper {
  def unapplyFromInternationalForm(obj: AddressDto): Some[
    (String, String, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String])
  ] = Some(
    (
      obj.line1,
      obj.line2.getOrElse(""),
      obj.line3,
      obj.line4OrTown,
      obj.line5OrCounty,
      obj.postcode,
      obj.country,
      obj.propertyRefNo
    )
  )

  @nowarn("msg=match may not be exhaustive.")
  def applyFromInternationalForm(
    line1: String,
    line2: String,
    line3: Option[String],
    line4: Option[String],
    line5orTown: Option[String],
    postcode: Option[String],
    country: Option[String],
    propertyRefNo: Option[String]
  ): AddressDto =
    List(line3, line4).filter(op => op.exists(line => line.nonEmpty)).padTo(2, None) match {
      case List(newLine3, newLine4) =>
        new AddressDto(line1, Some(line2), newLine3, newLine4, line5orTown, postcode, country, propertyRefNo)
    }

  private def applyFromUkForm(
    line1: String,
    line2: Option[String],
    line4OrTown: String,
    line5OrCounty: Option[String],
    postcode: Option[String],
    country: Option[String],
    propertyRefNo: Option[String]
  ): AddressDto =
    new AddressDto(
      line1,
      line2,
      None,
      Some(line4OrTown),
      line5OrCounty,
      postcode,
      country,
      propertyRefNo
    )

  private def unapplyFromUkForm(obj: AddressDto): Some[
    (String, Option[String], String, Option[String], Option[String], Option[String], Option[String])
  ] = Some(
    (
      obj.line1,
      obj.line2,
      obj.line4OrTown.getOrElse(""),
      obj.line5OrCounty,
      obj.postcode,
      obj.country,
      obj.propertyRefNo
    )
  )

  implicit val formats: OFormat[AddressDto] = Json.format[AddressDto]

  @nowarn("msg=match may not be exhaustive.")
  def fromAddressRecord(addressRecord: AddressRecord): AddressDto = {
    println("addressRecord.address.town " + addressRecord.address.town)
    val defaultPad = 3
    println("lines " + addressRecord.address.lines)
    addressRecord.address.lines
      .map(s => Option(s).filter(_.trim.nonEmpty))
      .padTo(defaultPad, None) match {
      case List(line1, line2, line3) =>
        AddressDto(
          line1.getOrElse(""),
          line2,
          line3,
          addressRecord.address.town,
          None,
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
      "line2"         -> optional(text)
        .verifying("error.line2_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line2_invalid_characters", e => validateAddressLineCharacters(e)),
      "line4OrTown"   -> text
        .verifying("error.line4OrTown_required", _.nonEmpty)
        .verifying("error.line4OrTown_contains_more_than_35_characters", _.length <= 35)
        .verifying("error.line4OrTown_invalid_characters", e => validateAddressLineCharacters(Some(e))),
      "line5OrCounty" -> optional(text)
        .verifying("error.line5OrCounty_contains_more_than_35_characters", e => e.fold(true)(_.length <= 35))
        .verifying("error.line5OrCounty_invalid_characters", e => validateAddressLineCharacters(e)),
      "postcode"      -> optional(text)
        .verifying("error.postcode_required", _.nonEmpty)
        .verifying(
          "error.enter_a_valid_uk_postcode",
          e =>
            e match {
              case Some(PostcodeRegex(_*)) => true
              case None                    => true // Avoid showing 2 error messages
              case _                       => false
            }
        ),
      "country"       -> optional(text),
      "propertyRefNo" -> optional(nonEmptyText)
    )(AddressDto.applyFromUkForm)(AddressDto.unapplyFromUkForm)
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
    )(AddressDto.applyFromInternationalForm)(AddressDto.unapplyFromInternationalForm)
  )
}
