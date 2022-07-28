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

package util

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.time.CurrentTaxYear
import util.DateTimeTools.defaultTZ

import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.util.{Failure, Success, Try}
import java.time.{LocalDate, ZoneId, ZonedDateTime, LocalDateTime => JavaLDT}
import java.util.Locale

object DateTimeTools extends CurrentTaxYear with Logging {

  //Timezone causing problem on dev server
  val defaultTZ = ZoneId.of("Europe/London")
  val unixDateFormat = "yyyy-MM-dd"
  val unixDateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss"
  val humanDateFormat = "dd MMMMM yyyy"

  //Returns for example 1516 in March 2016
  def previousAndCurrentTaxYear = previousAndCurrentTaxYearFromGivenYear(current.currentYear)

  def previousAndCurrentTaxYearFromGivenYear(year: Int) = {
    def y = year

    (y - 1).toString.takeRight(2) + y.toString.takeRight(2)
  }

  private def formatter(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern).withZone(defaultTZ)

  def short(dateTime: LocalDate) = dateTime.format(formatter("dd/MM/yyy"))

  def asHumanDateFromUnixDate(unixDate: String): String =
    Try(
      LocalDate
        .parse(unixDate)
        .format(
          DateTimeFormatter
            .ofLocalizedDate(FormatStyle.LONG)
            .withLocale(Locale.UK)
        )
    ) match {
      case Success(v) => v
      case Failure(e) =>
        logger.warn("Invalid date parse in DateTimeTools.asHumanDateFromUnixDate: " + e)
        unixDate
    }
  def toPaymentDate(dateTime: JavaLDT): LocalDate =
    LocalDate.of(dateTime.getYear, dateTime.getMonthValue, dateTime.getDayOfMonth)

  override def now: () => LocalDate = LocalDate.now
}

@Singleton
class DateTimeTools @Inject() () {

  def showSendTaxReturnByPost = {

    val start = LocalDate.parse(s"${LocalDate.now().getYear}-11-01T00:00:00Z")
    val end = LocalDate.parse(s"${LocalDate.now().getYear + 1}-01-31T23:59:59Z")
    !LocalDate.now().isAfter(start) && LocalDate.now().isBefore(end)
  }
}
