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
import org.scalatest.mockito.MockitoSugar
import play.api.mvc._

import scala.concurrent.Future

class FakeAuthJourney extends MockitoSugar {

  val mockAuthAction: AuthAction = mock[AuthAction]
  val mockSelfAssessment: SelfAssessmentStatusAction = mock[SelfAssessmentStatusAction]
  val mockGetPersonDetails: GetPersonDetailsAction = mock[GetPersonDetailsAction]

  def apply[A](userRequest: UserRequest[A]): AuthJourney =
    new AuthJourney(mockAuthAction, mockSelfAssessment, mockGetPersonDetails) {

      override val auth = new ActionBuilder[UserRequest[A]] {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(userRequest)
      }

    }

}

object FakeAuthJourney {
  def apply(userRequest: UserRequest[_]): AuthJourney = new FakeAuthJourney().apply(userRequest)
}
