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

package controllers.auth.requests

import models._
import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment}
import uk.gov.hmrc.domain.Nino

final case class UserRequest[+A](
  nino: Option[Nino],
  retrievedName: Option[UserName],
  saUserType: SelfAssessmentUserType,
  credentials: Credentials,
  confidenceLevel: ConfidenceLevel,
  personDetails: Option[PersonDetails],
  trustedHelper: Option[TrustedHelper],
  enrolments: Set[Enrolment],
  profile: Option[String],
  unreadMessageCount: Option[Int] = None,
  breadcrumb: Option[Breadcrumb] = None,
  request: Request[A]
) extends WrappedRequest[A](request) {

  def name: Option[String] = personDetails match {
    case Some(personDetails) => personDetails.person.shortName
    case _                   => retrievedName.map(_.toString)
  }

  def isSa: Boolean = saUserType != NonFilerSelfAssessmentUser

  def isSaUserLoggedIntoCorrectAccount: Boolean = saUserType match {
    case ActivatedOnlineFilerSelfAssessmentUser(_) => true
    case _                                         => false
  }

}
