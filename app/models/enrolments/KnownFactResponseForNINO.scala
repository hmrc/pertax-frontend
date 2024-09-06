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

package models.enrolments

import play.api.libs.json.{Format, Json}

case class KnownFactResponseForNINO(service: String, enrolments: List[EACDEnrolment]) {

  def getUTR: String =
    enrolments.head.identifiers.collect {
      case identifier if identifier.key == "UTR" => identifier.value
    }.head

  def getHMRCMTDIT: Option[String] =
    enrolments.head.verifiers.collect {
      case identifier if identifier.key == "MTDITID" => identifier.value
    }.headOption
}

object KnownFactResponseForNINO {
  implicit val format: Format[KnownFactResponseForNINO] =
    Json.format[KnownFactResponseForNINO]
}
