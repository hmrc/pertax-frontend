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

package util

import com.google.inject.Inject
import config.ConfigDecorator
import models._
import play.api.Logging
import play.api.i18n.Messages
import play.api.mvc.{Request, Result}
import play.api.mvc.Results.InternalServerError
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.domain.{Nino, SaUtr}
import views.html.InternalServerErrorView

class EnrolmentsHelper @Inject() (internalServerErrorView: InternalServerErrorView) extends Logging {

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
    enrolments: Set[Enrolment]
  ): Option[SelfAssessmentEnrolment] =
    enrolments
      .find(_.key == "IR-SA")
      .flatMap { enrolment =>
        enrolment.identifiers
          .find(id => id.key == "UTR")
          .map(key => SelfAssessmentEnrolment(SaUtr(key.value), fromString(enrolment.state)))
      }

  def singleAccountEnrolmentPresent(enrolments: Set[Enrolment], sessionNino: Nino)(implicit
    request: Request[_],
    configDecorator: ConfigDecorator,
    messages: Messages
  ): Either[Result, Boolean] =
    enrolments
      .filter(_.key == "HMRC-PT")
      .flatMap { enrolment =>
        enrolment.identifiers
          .filter(id => id.key == "NINO")
      } match {
      case enrolmentIdentifiers if enrolmentIdentifiers.isEmpty => Right(false)
      case enrolmentIdentifiers
          if enrolmentIdentifiers.exists(enrolmentIdentifier => Nino(enrolmentIdentifier.value) == sessionNino) =>
        Right(true)
      case _                                                    =>
        logger.error("The nino in HMRC-PT enrolment does not match the one from the user session")
        Left(InternalServerError(internalServerErrorView()))
    }
}
