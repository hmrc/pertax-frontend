/*
 * Copyright 2021 HM Revenue & Customs
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

package models

import org.joda.time.{DateTime, LocalDate}
import play.api.data.Forms._
import play.api.data.Mapping
import java.text.{DateFormatSymbols => JDateFormatSymbols}

import uk.gov.hmrc.play.mappers.DateTuple

import scala.util.Try

object DateTuple extends DateTuple

trait DateTuple {

  import uk.gov.hmrc.play.mappers.DateFields._

  val dateTuple: Mapping[Option[LocalDate]] = dateTuple(validate = true)

  def mandatoryDateTuple(error: String): Mapping[LocalDate] =
    dateTuple.verifying(error, data => data.isDefined).transform(o => o.get, v => if (v == null) None else Some(v))

  def dateTuple(validate: Boolean = true) =
    tuple(
      year  -> optional(text),
      month -> optional(text),
      day   -> optional(text)
    ).verifying(
        "error.date.required.year",
        data =>
          (data._1, data._2, data._3) match {
            case (None, None, None) => true
            case (None, _, _)       => false
            case (_, _, _)          => true
        })
      .verifying(
        "error.date.required.month",
        data =>
          (data._1, data._2, data._3) match {
            case (None, None, None) => true
            case (_, None, _)       => false
            case (_, _, _)          => true
        })
      .verifying(
        "error.date.required.day",
        data =>
          (data._1, data._2, data._3) match {
            case (None, None, None) => true
            case (_, _, None)       => false
            case (_, _, _)          => true
        })
      .verifying(
        "error.invalid.date.format",
        data => {

          (data._1, data._2, data._3) match {
            case (None, None, None) => true
            case (None, _, _)       => true
            case (_, None, _)       => true
            case (_, _, None)       => true
            case (yearOption, monthOption, dayOption) =>
              try {
                val y = yearOption.getOrElse(throw new Exception("Year missing")).trim
                if (y.length != 4) {
                  throw new Exception("Year must be 4 digits")
                }
                new LocalDate(
                  y.toInt,
                  monthOption.getOrElse(throw new Exception("Month missing")).trim.toInt,
                  dayOption.getOrElse(throw new Exception("Day missing")).trim.toInt)
                true
              } catch {
                case _: Throwable =>
                  if (validate) {
                    false
                  } else {
                    true
                  }
              }
          }

        }
      )
      .transform(
        {
          case (Some(y), Some(m), Some(d)) =>
            try {
              Some(new LocalDate(y.trim.toInt, m.trim.toInt, d.trim.toInt))
            } catch {
              case e: Exception =>
                if (validate) {
                  throw e
                } else {
                  None
                }
            }
          case (a, b, c) => None
        },
        (date: Option[LocalDate]) =>
          date match {
            case Some(d) => (Some(d.getYear.toString), Some(d.getMonthOfYear.toString), Some(d.getDayOfMonth.toString))
            case _       => (None, None, None)
        }
      )

  def validDateTuple: Mapping[DateTime] = {

    def verifyDigits(triple: (String, String, String)) =
      triple._1.forall(_.isDigit) && triple._2.forall(_.isDigit) && triple._3.forall(_.isDigit)

    tuple(
      "year"  -> optional(text),
      "month" -> optional(text),
      "day"   -> optional(text)
    ).verifying("error.enter_a_date", x => x._1.isDefined && x._2.isDefined && x._3.isDefined)
      .transform[(String, String, String)](
        x => (x._1.get.trim, x._2.get.trim, x._3.get.trim),
        x => (Some(x._1), Some(x._2), Some(x._3)))
      .verifying("error.enter_numbers", verifyDigits _)
      .verifying(
        "error.enter_valid_date",
        x => !verifyDigits(x) || Try(new DateTime(x._1.toInt, x._2.toInt, x._3.toInt, 0, 0)).isSuccess)
      .transform[DateTime](
        x => new DateTime(x._1.toInt, x._2.toInt, x._3.toInt, 0, 0).withTimeAtStartOfDay,
        x => (x.getYear.toString, x.getMonthOfYear.toString, x.getDayOfMonth.toString))
  }

}

object DateFields {
  val day = "day"
  val month = "month"
  val year = "year"
}

object DateFormatSymbols {

  val months = new JDateFormatSymbols().getMonths

  val monthsWithIndexes = months.zipWithIndex.take(12).map { case (s, i) => ((i + 1).toString, s) }.toSeq
}
