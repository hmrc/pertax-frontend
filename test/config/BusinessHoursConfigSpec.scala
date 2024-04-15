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

  private val standardValues: Seq[(String, Any)] = Seq(
    "feature.business-hours.0.day"        -> "Monday",
    "feature.business-hours.0.start-time" -> "00:00",
    "feature.business-hours.0.end-time"   -> "23:59",
    "feature.business-hours.1.day"        -> "Tuesday",
    "feature.business-hours.1.start-time" -> "00:00",
    "feature.business-hours.1.end-time"   -> "23:59",
    "feature.business-hours.2.day"        -> "Wednesday",
    "feature.business-hours.2.start-time" -> "00:00",
    "feature.business-hours.2.end-time"   -> "23:59",
    "feature.business-hours.3.day"        -> "Thursday",
    "feature.business-hours.3.start-time" -> "00:00",
    "feature.business-hours.3.end-time"   -> "23:59",
    "feature.business-hours.4.day"        -> "Friday",
    "feature.business-hours.4.start-time" -> "00:00",
    "feature.business-hours.4.end-time"   -> "23:59",
    "feature.business-hours.5.day"        -> "Saturday",
    "feature.business-hours.5.start-time" -> "00:00",
    "feature.business-hours.5.end-time"   -> "23:59",
    "feature.business-hours.6.day"        -> "Sunday",
    "feature.business-hours.6.start-time" -> "00:00",
    "feature.business-hours.6.end-time"   -> "23:59"
  )

  "get" must {
    "read configuration" in {
      val updatedValues: Seq[(String, Any)] = Seq(
        "feature.business-hours.0.day"        -> "Monday",
        "feature.business-hours.0.start-time" -> "9:00",
        "feature.business-hours.0.end-time"   -> "17:00",
        "play.cache.bindCaches"               -> List("controller-cache", "document-cache"),
        "play.cache.createBoundCaches"        -> false
      )
      val app                               = localGuiceApplicationBuilder()
        .configure(standardValues ++ updatedValues: _*)
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe
        Map(
          DayOfWeek.MONDAY    -> (LocalTime.of(9, 0), LocalTime.of(17, 0)),
          DayOfWeek.TUESDAY   -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
          DayOfWeek.WEDNESDAY -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
          DayOfWeek.THURSDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
          DayOfWeek.FRIDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
          DayOfWeek.SATURDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
          DayOfWeek.SUNDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59))
        )

    }
  }

  "ignore configuration" when {
    "end time before start time" in {
      val updatedValues: Seq[(String, Any)] = Seq(
        "feature.business-hours.0.day"        -> "Monday",
        "feature.business-hours.0.start-time" -> "17:00",
        "feature.business-hours.0.end-time"   -> "9:00",
        "play.cache.bindCaches"               -> List("controller-cache", "document-cache"),
        "play.cache.createBoundCaches"        -> false
      )
      val app                               = localGuiceApplicationBuilder()
        .configure(standardValues ++ updatedValues: _*)
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map(
        DayOfWeek.TUESDAY   -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.WEDNESDAY -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.THURSDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.FRIDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SATURDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SUNDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59))
      )
    }

    "start time is invalid" in {
      val updatedValues: Seq[(String, Any)] = Seq(
        "feature.business-hours.0.day"        -> "Monday",
        "feature.business-hours.0.start-time" -> "giberish",
        "feature.business-hours.0.end-time"   -> "17:00",
        "play.cache.bindCaches"               -> List("controller-cache", "document-cache"),
        "play.cache.createBoundCaches"        -> false
      )
      val app                               = localGuiceApplicationBuilder()
        .configure(standardValues ++ updatedValues: _*)
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map(
        DayOfWeek.TUESDAY   -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.WEDNESDAY -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.THURSDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.FRIDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SATURDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SUNDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59))
      )
    }

    "end time is invalid" in {
      val updatedValues: Seq[(String, Any)] = Seq(
        "feature.business-hours.0.day"        -> "Monday",
        "feature.business-hours.0.start-time" -> "9:00",
        "feature.business-hours.0.end-time"   -> "giberish",
        "play.cache.bindCaches"               -> List("controller-cache", "document-cache"),
        "play.cache.createBoundCaches"        -> false
      )
      val app                               = localGuiceApplicationBuilder()
        .configure(standardValues ++ updatedValues: _*)
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map(
        DayOfWeek.TUESDAY   -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.WEDNESDAY -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.THURSDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.FRIDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SATURDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SUNDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59))
      )
    }

    "day is invalid" in {
      val updatedValues: Seq[(String, Any)] = Seq(
        "feature.business-hours.0.day"        -> "Invalid",
        "feature.business-hours.0.start-time" -> "9:00",
        "feature.business-hours.0.end-time"   -> "17:00",
        "play.cache.bindCaches"               -> List("controller-cache", "document-cache"),
        "play.cache.createBoundCaches"        -> false
      )
      val app                               = localGuiceApplicationBuilder()
        .configure(standardValues ++ updatedValues: _*)
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map(
        DayOfWeek.TUESDAY   -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.WEDNESDAY -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.THURSDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.FRIDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SATURDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SUNDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59))
      )
    }

    "end-time is missing" in {
      val updatedValues: Seq[(String, Any)] = Seq(
        "feature.business-hours.0.day"        -> "Monday",
        "feature.business-hours.0.start-time" -> "9:00",
        "feature.business-hours.0.end-time"   -> null,
        "play.cache.bindCaches"               -> List("controller-cache", "document-cache"),
        "play.cache.createBoundCaches"        -> false
      )
      val app                               = localGuiceApplicationBuilder()
        .configure(standardValues ++ updatedValues: _*)
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map(
        DayOfWeek.TUESDAY   -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.WEDNESDAY -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.THURSDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.FRIDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SATURDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SUNDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59))
      )
    }

    "start-time is missing" in {
      val updatedValues: Seq[(String, Any)] = Seq(
        "feature.business-hours.0.day"        -> "Monday",
        "feature.business-hours.0.start-time" -> null,
        "feature.business-hours.0.end-time"   -> "9:00",
        "play.cache.bindCaches"               -> List("controller-cache", "document-cache"),
        "play.cache.createBoundCaches"        -> false
      )
      val app                               = localGuiceApplicationBuilder()
        .configure(standardValues ++ updatedValues: _*)
        .build()

      val sut = app.injector.instanceOf[BusinessHoursConfig]

      sut.get mustBe Map(
        DayOfWeek.TUESDAY   -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.WEDNESDAY -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.THURSDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.FRIDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SATURDAY  -> (LocalTime.of(0, 0), LocalTime.of(23, 59)),
        DayOfWeek.SUNDAY    -> (LocalTime.of(0, 0), LocalTime.of(23, 59))
      )
    }
  }
}
