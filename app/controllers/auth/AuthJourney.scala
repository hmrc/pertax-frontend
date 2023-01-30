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

package controllers.auth

import com.google.inject.{ImplementedBy, Inject}
import controllers.auth.requests.UserRequest
import play.api.mvc.{ActionBuilder, AnyContent}

@ImplementedBy(classOf[AuthJourneyImpl])
trait AuthJourney {
  val authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent]
//  val authWithSelfAssessment: ActionBuilder[UserRequest, AnyContent]
//  val minimumAuthWithSelfAssessment: ActionBuilder[UserRequest, AnyContent]
}

class AuthJourneyImpl @Inject() (
  authAction: AuthAction,
  selfAssessmentStatusAction: SelfAssessmentStatusAction,
  getPersonDetailsAction: GetPersonDetailsAction
) extends AuthJourney {

  override val authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
    authAction andThen selfAssessmentStatusAction andThen getPersonDetailsAction

//  override val authWithSelfAssessment: ActionBuilder[UserRequest, AnyContent] =
//    authAction andThen selfAssessmentStatusAction

  // TODO - Delete once verified
//  override val minimumAuthWithSelfAssessment: ActionBuilder[UserRequest, AnyContent] =
//    minimumAuthAction andThen selfAssessmentStatusAction

}
