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

import com.google.inject.Inject
import controllers.auth.requests.UserRequest
import play.api.mvc.ActionBuilder

class AuthJourney @Inject()(
  authAction: AuthAction,
  minimumAuthAction: MinimumAuthAction,
  selfAssessmentStatusAction: SelfAssessmentStatusAction,
  getPersonDetailsAction: GetPersonDetailsAction) {

  val authWithPersonalDetails
    : ActionBuilder[UserRequest] = authAction andThen selfAssessmentStatusAction andThen getPersonDetailsAction

  val authWithSelfAssessment: ActionBuilder[UserRequest] = authAction andThen selfAssessmentStatusAction

  val minimumAuthWithSelfAssessment: ActionBuilder[UserRequest] = minimumAuthAction andThen selfAssessmentStatusAction

}
