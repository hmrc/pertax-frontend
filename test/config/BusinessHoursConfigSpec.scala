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

package config

import testUtils.BaseSpec
import java.time.{DayOfWeek, LocalTime}

class BusinessHoursConfigSpec extends BaseSpec {

  "get" must {
    "read configuration" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.business-hours.Monday.start-time" -> "9:00",
          "feature.business-hours.Monday.end-time"   -> "17:00"
        )
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map(DayOfWeek.MONDAY -> (LocalTime.of(9, 0), LocalTime.of(17, 0)))

    }
  }

  "ignore configuration" when {
    "No key is present" in {
      val app = localGuiceApplicationBuilder().build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map.empty
    }

    "end time before start time" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.business-hours.Monday.start-time" -> "17:00",
          "feature.business-hours.Monday.end-time"   -> "9:00"
        )
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map.empty
    }

    "start time is invalid" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.business-hours.Monday.start-time" -> "giberish",
          "feature.business-hours.Monday.end-time"   -> "17:00"
        )
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map.empty
    }

    "end time is invalid" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.business-hours.Monday.start-time" -> "9:00",
          "feature.business-hours.Monday.end-time"   -> "giberish"
        )
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map.empty
    }

    "day is invalid" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.business-hours.Giberish.start-time" -> "9:00",
          "feature.business-hours.Giberish.end-time"   -> "17:00"
        )
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map.empty
    }

    "end-time is missing" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.business-hours.Monday.start-time" -> "9:00"
        )
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map.empty
    }

    "start-time is missing" in {
      val app = localGuiceApplicationBuilder()
        .configure(
          "feature.business-hours.Monday.end-time" -> "9:00"
        )
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map.empty
    }
  }
}
