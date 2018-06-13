/*
 * Copyright 2018 HM Revenue & Customs
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

import org.joda.time.{DateTime, _}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.Logger
import uk.gov.hmrc.time.TaxYearResolver
import scala.util.{Failure, Success, Try}


object DateTimeTools {

  //Timezone causing problem on dev server
  val defaultTZ = DateTimeZone.forID("Europe/London")
  val unixDateFormat = "yyyy-MM-dd"
  val unixDateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss"
  val humanDateFormat = "dd MMMMM yyyy"

  //Returns for example 1516 in March 2016
  def previousAndCurrentTaxYear = previousAndCurrentTaxYearFromGivenYear(TaxYearResolver.currentTaxYear)

  def previousAndCurrentTaxYearFromGivenYear(year: Int) = {
    def y = year
    (y-1).toString.takeRight(2) + (y).toString.takeRight(2)
  }

  private def formatter(pattern: String): DateTimeFormatter = DateTimeFormat.forPattern(pattern).withZone(defaultTZ)

  def short(dateTime: DateTime) = formatter("dd/MM/yyy").print(dateTime) //FIXME - remove and use LocalDate instead

  private def parseUnixDate(date: String): Try[String] =
    Try(DateTimeFormat.forPattern(humanDateFormat).print(DateTime.parse(date)))

  def asHumanDateFromUnixDate(unixDate: String): String = {
    parseUnixDate(unixDate) match {
      case Success(v) => v
      case Failure(e) => {
        Logger.warn("Invalid date parse in DateTimeTools.asHumanDateFromUnixDate: " + e)
        unixDate
      }
    }
  }
}
