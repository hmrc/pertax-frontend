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

package config

import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}

import java.time.{DayOfWeek, LocalTime}
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import scala.collection.JavaConverters._

@Singleton
class BusinessHoursConfig @Inject() (configuration: Configuration) extends Logging {

  private[BusinessHoursConfig] def stringToDayOfWeek(value: String): Option[DayOfWeek] =
    value.toUpperCase() match {
      case "MONDAY"    => Some(DayOfWeek.MONDAY)
      case "TUESDAY"   => Some(DayOfWeek.TUESDAY)
      case "WEDNESDAY" => Some(DayOfWeek.WEDNESDAY)
      case "THURSDAY"  => Some(DayOfWeek.THURSDAY)
      case "FRIDAY"    => Some(DayOfWeek.FRIDAY)
      case "SATURDAY"  => Some(DayOfWeek.SATURDAY)
      case "SUNDAY"    => Some(DayOfWeek.SUNDAY)
      case weekDay =>
        val ex = new RuntimeException(s"Invalid day of week `$weekDay`. Check `app-config-*`")
        logger.error(ex.getMessage, ex)
        None
    }

  def get: Map[DayOfWeek, (LocalTime, LocalTime)] = {
    val config = configuration.underlying

    if (config.hasPathOrNull("feature.business-hours")) {
      config
        .getObject("feature.business-hours")
        .asScala
        .map { case (dayOfWeek, _) =>
          val formatter = DateTimeFormatter.ofPattern("H:m").withZone(TimeZone.getTimeZone("Europe/London").toZoneId)
          val localStartTime =
            LocalTime.parse(configuration.get[String](s"feature.business-hours.$dayOfWeek.start-time"), formatter)
          val localEndTime =
            LocalTime.parse(configuration.get[String](s"feature.business-hours.$dayOfWeek.end-time"), formatter)

          stringToDayOfWeek(dayOfWeek).map(_ -> (localStartTime, localEndTime))
        }
        .flatten
        .toMap
    } else {
      Map.empty
    }
  }
}
