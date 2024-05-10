/*
 * Copyright 2024 HM Revenue & Customs
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
import models.{PersonDetails, SelfAssessmentUserType}
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, DefaultActionBuilder, MessagesControllerComponents, Request, Result}
import testUtils.UserRequestFixture.buildUserRequest

import scala.concurrent.{ExecutionContext, Future}

class FakeAuthJourney @Inject() (
  authAction: AuthRetrievals,
  selfAssessmentStatusAction: SelfAssessmentStatusAction,
  pertaxAuthAction: PertaxAuthAction,
  getPersonDetailsAction: GetPersonDetailsAction,
  defaultActionBuilder: DefaultActionBuilder,
  saUser: SelfAssessmentUserType,
  mcc: MessagesControllerComponents,
  personDetails: Option[PersonDetails] = None
) extends AuthJourney(authAction, selfAssessmentStatusAction, pertaxAuthAction, getPersonDetailsAction, defaultActionBuilder) {

  private val actionBuilderFixture: ActionBuilder[UserRequest, AnyContent] =
    new ActionBuilder[UserRequest, AnyContent] {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(
            saUser = saUser,
            personDetails = personDetails,
            request = request
          )
        )

      override def parser: BodyParser[AnyContent] = mcc.parsers.defaultBodyParser

      override protected implicit val executionContext: ExecutionContext = mcc.executionContext
    }

  override val authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] = actionBuilderFixture
}
