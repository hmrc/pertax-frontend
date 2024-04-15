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

import com.google.inject.{Inject, Singleton}
import com.typesafe.config.ConfigException
import play.api.{Configuration, Logging}

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{DayOfWeek, LocalTime}
import java.util.TimeZone
import scala.util.{Failure, Success, Try}

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
      case weekDay     =>
        val ex = new RuntimeException(s"Invalid day of week `$weekDay`. Check `app-config-*`")
        logger.error(ex.getMessage, ex)
        None
    }

  private def parseDateTime(fieldName: String): Option[LocalTime] = {
    val formatter = DateTimeFormatter.ofPattern("H:m").withZone(TimeZone.getTimeZone("Europe/London").toZoneId)
    Try(
      LocalTime.parse(configuration.get[String](fieldName), formatter)
    ) match {
      case Success(localTime)                         => Some(localTime)
      case Failure(exception: DateTimeParseException) =>
        logger.error(exception.getMessage, exception)
        None
      case Failure(exception: ConfigException)        =>
        logger.error(exception.getMessage, exception)
        None
      case Failure(error)                             => throw error
    }
  }

  def get: Map[DayOfWeek, (LocalTime, LocalTime)] =
    (0 to 6).flatMap { i =>
      configuration.getOptional[String](s"feature.business-hours.$i.day") match {
        case None            => None
        case Some(dayOfWeek) =>
          val localStartTime = parseDateTime(s"feature.business-hours.$i.start-time")
          val localEndTime   = parseDateTime(s"feature.business-hours.$i.end-time")
          (localStartTime, localEndTime) match {
            case (Some(start), Some(end)) =>
              if (end.isBefore(start)) {
                val ex = new RuntimeException(s"End time cannot be before start time")
                logger.error(ex.getMessage, ex)
                None
              } else {
                stringToDayOfWeek(dayOfWeek).map(_ -> (start, end))
              }
            case _                        => None
          }
      }
    }.toMap
}
