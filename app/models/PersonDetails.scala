/*
 * Copyright 2019 HM Revenue & Customs
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

package models

import org.joda.time.{Instant, LocalDate}
import util.DateTimeTools
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino


object Person {
  implicit val formats = {
    implicit val localDateReads = new Reads[LocalDate] {  //FIXME - Temporary compatibility fix, remove when citizen-details >= 2.23.0
      override def reads(json: JsValue): JsResult[LocalDate] = json match {
        case JsNumber(num) => JsSuccess((new Instant(num.toLong)).toDateTime( DateTimeTools.defaultTZ ).toLocalDate)
        case other => implicitly[Reads[LocalDate]].reads(other)
      }
    }
    Json.format[Person]
  }
}
case class Person(
  firstName: Option[String],
  middleName: Option[String],
  lastName: Option[String],
  initials: Option[String],
  title: Option[String],
  honours: Option[String],
  sex: Option[String],
  dateOfBirth: Option[LocalDate],
  nino: Option[Nino]
) {
  lazy val initialsName = initials.getOrElse(List(title, firstName.map(_.take(1)), middleName.map(_.take(1)), lastName).flatten.mkString(" "))
  lazy val shortName = for (f <- firstName; l <- lastName) yield List(f, l).mkString(" ")
  lazy val fullName = List(title, firstName, middleName, lastName, honours).flatten.mkString(" ")
}


object Address {

  implicit val formats = {
    implicit val localDateReads = new Reads[LocalDate] {  //FIXME - Temporary compatibility fix, remove when citizen-details >= 2.23.0
      override def reads(json: JsValue): JsResult[LocalDate] = json match {
        case JsNumber(num) => JsSuccess((new Instant(num.toLong)).toDateTime( DateTimeTools.defaultTZ ).toLocalDate)
        case other => implicitly[Reads[LocalDate]].reads(other)
      }
    }
    Json.format[Address]
  }
}

case class Address(
  line1: Option[String],
  line2: Option[String],
  line3: Option[String],
  line4: Option[String],
  line5: Option[String],
  postcode: Option[String],
  startDate: Option[LocalDate],
  `type`: Option[String]
) {
  lazy val lines = List(line1, line2, line3, line4, line5).flatten

  def isWelshLanguageUnit: Boolean = {
   val welshLanguageUnitPostcodes = Set("CF145SH", "CF145TS", "LL499BF", "BX55AB", "LL499AB")
    welshLanguageUnitPostcodes.contains(postcode.getOrElse("").toUpperCase.trim.replace(" ", ""))
  }
}

object PersonDetails {
  implicit val formats = Json.format[PersonDetails]
}
case class PersonDetails(
  etag: String,
  person: Person,
  address: Option[Address],
  correspondenceAddress: Option[Address]
)
