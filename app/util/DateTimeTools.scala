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

package util

import play.api.Logging
import uk.gov.hmrc.time.CurrentTaxYear

import java.time.LocalDate

object DateTimeTools extends CurrentTaxYear with Logging {
  // Returns for example 1516 in March 2016
  def previousAndCurrentTaxYear: String = previousAndCurrentTaxYearFromGivenYear(current.currentYear)

  def previousAndCurrentTaxYearFromGivenYear(year: Int): String = {
    def y = year
    (y - 1).toString.takeRight(2) + y.toString.takeRight(2)
  }

  override def now: () => LocalDate = () => LocalDate.now
}
