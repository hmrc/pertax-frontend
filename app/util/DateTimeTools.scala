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

package util

import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, _}
import play.api.Logger
import uk.gov.hmrc.time.CurrentTaxYear

import scala.util.{Failure, Success, Try}

object DateTimeTools extends CurrentTaxYear {

  //Timezone causing problem on dev server
  val defaultTZ = DateTimeZone.forID("Europe/London")
  val unixDateFormat = "yyyy-MM-dd"
  val unixDateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss"
  val humanDateFormat = "dd MMMMM yyyy"

  //Returns for example 1516 in March 2016
  def previousAndCurrentTaxYear = previousAndCurrentTaxYearFromGivenYear(current.currentYear)

  def previousAndCurrentTaxYearFromGivenYear(year: Int) = {
    def y = year
    (y - 1).toString.takeRight(2) + (y).toString.takeRight(2)
  }

  def showSendTaxReturnByPost = {

    val start = new DateTime(s"${DateTime.now().getYear}-11-01T00:00:00Z")
    val end = new DateTime(s"${DateTime.now().getYear + 1}-01-31T23:59:59Z")
    !DateTime.now().isAfter(start) && DateTime.now().isBefore(end)
  }

  private def formatter(pattern: String): DateTimeFormatter = DateTimeFormat.forPattern(pattern).withZone(defaultTZ)

  def short(dateTime: LocalDate) = formatter("dd/MM/yyy").print(dateTime)

  def asHumanDateFromUnixDate(unixDate: String): String =
    Try(DateTimeFormat.forPattern(humanDateFormat).print(DateTime.parse(unixDate))) match {
      case Success(v) => v
      case Failure(e) => {
        Logger.warn("Invalid date parse in DateTimeTools.asHumanDateFromUnixDate: " + e)
        unixDate
      }
    }

  override def now: () => DateTime = DateTime.now
}
