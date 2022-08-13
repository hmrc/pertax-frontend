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

import models._
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.domain.SaUtr

class EnrolmentsHelper {

  private def fromString(value: String): EnrolmentStatus = value match {
    case "Activated"       => Activated
    case "NotYetActivated" => NotYetActivated
    case _                 => throw new RuntimeException(s"Unexpected enrolment status of $value was returned")
  }

  def itsaEnrolmentStatus(enrolments: Set[Enrolment]): Option[ItsaEnrolment] =
    enrolments
      .find(_.key == "HMRC-MTD-IT")
      .flatMap { enrolment =>
        enrolment.identifiers
          .find(id => id.key == "MTDITID")
          .map(_ => ItsaEnrolmentEnrolled(fromString(enrolment.state)))
      }

  def selfAssessmentStatus(
    enrolments: Set[Enrolment],
    trustedHelper: Option[TrustedHelper]
  ): Option[SelfAssessmentEnrolment] =
    enrolments
      .find(_.key == "IR-SA" && trustedHelper.isEmpty)
      .flatMap { enrolment =>
        enrolment.identifiers
          .find(id => id.key == "UTR")
          .map(key => SelfAssessmentEnrolment(SaUtr(key.value), fromString(enrolment.state)))
      }

  def singleAccountEnrolmentPresent(enrolments: Set[Enrolment]): Boolean =
    enrolments
      .find(_.key == "HMRC-PT")
      .flatMap { enrolment =>
        enrolment.identifiers
          .find(id => id.key == "NINO")
      }
      .nonEmpty
}
