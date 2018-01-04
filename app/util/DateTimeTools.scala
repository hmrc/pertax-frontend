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

import uk.gov.hmrc.time.TaxYearResolver
import org.joda.time._
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.DateTime


object DateTimeTools {

  //Timezone causing problem on dev server
  val defaultTZ = DateTimeZone.forID("Europe/London")
  val unixDateFormat = "yyyy-MM-dd"
  val unixDateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss"

  //Returns for example 1516 in March 2016
  def previousAndCurrentTaxYear = previousAndCurrentTaxYearFromGivenYear(TaxYearResolver.currentTaxYear)

  def previousAndCurrentTaxYearFromGivenYear(year: Int) = {
    def y = year
    (y-1).toString.takeRight(2) + (y).toString.takeRight(2)
  }

  def asDateFromUnixDate(s: String): DateTime =
    DateTime.parse(s, DateTimeFormat.forPattern(unixDateFormat).withZone(defaultTZ))

  def asDateFromUnixDateTime(s: String): DateTime =
    DateTime.parse(s, DateTimeFormat.forPattern(unixDateTimeFormat).withZone(defaultTZ))

  private def formatter(pattern: String): DateTimeFormatter = DateTimeFormat.forPattern(pattern).withZone(DateTimeZone.forID("Europe/London"))

  def short(dateTime: DateTime) = formatter("dd/MM/yyy").print(dateTime)

}
