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

package controllers.auth

import com.google.inject.{ImplementedBy, Inject}
import controllers.auth.requests.UserRequest
import play.api.mvc.ActionBuilder

@ImplementedBy(classOf[AuthJourneyImpl])
trait AuthJourney {
  val authWithPersonalDetails: ActionBuilder[UserRequest]
  val authWithSelfAssessment: ActionBuilder[UserRequest]
  val minimumAuthWithSelfAssessment: ActionBuilder[UserRequest]
}

class AuthJourneyImpl @Inject()(
  authAction: AuthAction,
  minimumAuthAction: MinimumAuthAction,
  selfAssessmentStatusAction: SelfAssessmentStatusAction,
  getPersonDetailsAction: GetPersonDetailsAction)
    extends AuthJourney {

  override val authWithPersonalDetails
    : ActionBuilder[UserRequest] = authAction andThen selfAssessmentStatusAction andThen getPersonDetailsAction

  override val authWithSelfAssessment: ActionBuilder[UserRequest] = authAction andThen selfAssessmentStatusAction

  override val minimumAuthWithSelfAssessment
    : ActionBuilder[UserRequest] = minimumAuthAction andThen selfAssessmentStatusAction

}
