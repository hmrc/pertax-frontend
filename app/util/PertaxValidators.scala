/*
 * Copyright 2020 HM Revenue & Customs
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

package util

import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{FieldMapping, FormError}

object PertaxValidators {

  type FormDataValidator = (String, Map[String, String]) => Seq[FormError]

  def fieldsArePresentIfCurrentFieldIsMissingFormDataValidator(
    fieldsToCheck: String*)(key: String, formData: Map[String, String]): Seq[FormError] =
    formData.getOrElse(key, "") match {
      case "" =>
        val anyCheckedFieldContainsData =
          fieldsToCheck.foldLeft[Boolean](false)((dataFound, rf) => dataFound || !formData.getOrElse(rf, "").isEmpty)
        if (anyCheckedFieldContainsData) Seq(FormError(key, s"error.${key}_required")) else Nil
      case _ =>
        Nil
    }

  def optionalTextIfFieldsHaveContent(requiredFields: String*) =
    optionalTextIfFormDataValidatesMapping(fieldsArePresentIfCurrentFieldIsMissingFormDataValidator(requiredFields: _*))

  def optionalTextIfFormDataValidatesMapping(formDataValidator: FormDataValidator): FieldMapping[Option[String]] =
    of(new Formatter[Option[String]] {

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {

        def formValueUnlessEmptyString: Option[String] = {
          val v = data.get(key)
          if (v.fold(true)(_.isEmpty)) None else v
        }

        formDataValidator(key, data) match {
          case Nil        => Right(formValueUnlessEmptyString)
          case formErrors => Left(formErrors)
        }
      }

      override def unbind(key: String, value: Option[String]) =
        value.fold[Map[String, String]](Map.empty)(v => Map(key -> v))
    })

  private val AddressLineRegex = """^[A-Za-z0-9 \-,.&'\/]+""".r
  val PostcodeRegex =
    """^(GIR ?0AA|[A-PR-UWYZa-pr-uwyz]([0-9]{1,2}|([A-HK-Ya-jk-y][0-9]([0-9ABEHMNPRV-Yabehmnprv-y])?)|[0-9][A-HJKPS-UWa-hjkps-u])\s?[0-9][ABD-HJLNP-UW-Zabd-hjlnp-uw-z]{2})$""".r

  def validateAddressLineCharacters(addressLine: Option[String]) = addressLine match {
    case Some(line) =>
      line match {
        case AddressLineRegex() => true
        case ""                 => true
        case _                  => false
      }
    case None => true
  }

}
