/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.auth.requests

import models.UserName
import org.joda.time.DateTime
import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment}
import uk.gov.hmrc.domain.{Nino, SaUtr}

sealed trait SelfAssessmentStatus
case object Activated extends SelfAssessmentStatus
case object NotYetActivated extends SelfAssessmentStatus

object SelfAssessmentStatus {

  def fromString(value: String): SelfAssessmentStatus =
    value match {
      case "Activated"       => Activated
      case "NotYetActivated" => NotYetActivated
      case _ =>
        throw new RuntimeException(
          s"Unexpected Self Assessment enrolment status of $value was returned"
        )
    }
}

case class SelfAssessmentEnrolment(saUtr: SaUtr, status: SelfAssessmentStatus)

case class AuthenticatedRequest[A](
  nino: Option[Nino],
  saEnrolment: Option[SelfAssessmentEnrolment],
  credentials: Credentials,
  confidenceLevel: ConfidenceLevel,
  name: Option[UserName],
  trustedHelper: Option[TrustedHelper],
  profile: Option[String],
  enrolments: Set[Enrolment],
  request: Request[A]
) extends WrappedRequest[A](request)
