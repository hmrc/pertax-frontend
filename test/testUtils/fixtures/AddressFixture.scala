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

package testUtils.fixtures

import models.Address
import java.time.LocalDate

object AddressFixture {

  def address(
    line1: Option[String] = None,
    line2: Option[String] = None,
    line3: Option[String] = None,
    line4: Option[String] = None,
    postcode: Option[String] = None,
    country: Option[String] = None,
    startDate: Option[LocalDate] = None,
    endDate: Option[LocalDate] = None,
    `type`: Option[String] = None,
    isRls: Boolean = false
  ): Address =
    Address(line1, line2, line3, line4, postcode, country, startDate, endDate, `type`, isRls)

}
