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

import com.google.inject.Inject
import config.BusinessHoursConfig

import java.time.{DayOfWeek, LocalDateTime, LocalTime}

class BusinessHours @Inject() (businessHoursConfig: BusinessHoursConfig) {

  def isTrue(dateTime: LocalDateTime): Boolean = {
    val dayOfWeek: DayOfWeek = dateTime.getDayOfWeek
    val time: LocalTime      = dateTime.toLocalTime

    businessHoursConfig.get.get(dayOfWeek).exists { case (start, end) =>
      time.compareTo(start) >= 0 && time.compareTo(end) <= 0
    }
  }

}
