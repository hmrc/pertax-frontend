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

import controllers.auth.requests.UserRequest
import models.SelfAssessmentUserType
import play.api.mvc.{ActionBuilder, Request, Result}
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.Future

class FakeAuthJourney(saUser: SelfAssessmentUserType) extends AuthJourney {
  private val actionBuilder = new ActionBuilder[UserRequest] {
    override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
      block(
        buildUserRequest(
          saUser = saUser,
          request = request
        ))
  }

  override val authWithPersonalDetails: ActionBuilder[UserRequest] = actionBuilder
  override val authWithSelfAssessment: ActionBuilder[UserRequest] = actionBuilder
  override val minimumAuthWithSelfAssessment: ActionBuilder[UserRequest] = actionBuilder
}
