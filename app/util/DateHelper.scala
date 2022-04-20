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

object DateHelper {

  implicit class JodaTimeConverters(val jodaLocalDate: org.joda.time.LocalDate) extends AnyVal {
    def toJavaLocalDate: java.time.LocalDate = java.time.LocalDate.parse(jodaLocalDate.toString)
  }

  implicit class JodaDateTimeConverters(val jodaDateTime: org.joda.time.DateTime) extends AnyVal {
    def toJavaLocalDateTime: java.time.LocalDateTime = java.time.LocalDateTime.parse(jodaDateTime.toString)
  }
}
