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

package controllers.auth.requests

import models.{NonFilerSelfAssessmentUser, PersonDetails, SelfAssessmentUserType, UserName}
import org.joda.time.DateTime
import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.renderer.ActiveTab

case class UserRequest[A](
  nino: Option[Nino],
  retrievedName: Option[UserName],
  previousLoginTime: Option[DateTime],
  saUserType: SelfAssessmentUserType,
  authProvider: String,
  confidenceLevel: ConfidenceLevel,
  personDetails: Option[PersonDetails],
  unreadMessageCount: Option[Int] = None,
  activeTab: Option[ActiveTab] = None,
  request: Request[A])
    extends WrappedRequest[A](request) {

  def name: Option[String] = personDetails match {
    case Some(personDetails) => personDetails.person.shortName
    case _                   => Some(retrievedName.toString)
  }

  def isGovernmentGateway: Boolean = authProvider == "GovernmentGateway"

  def isVerify: Boolean = authProvider == "Verify"

  def isSa: Boolean = saUserType != NonFilerSelfAssessmentUser

}
