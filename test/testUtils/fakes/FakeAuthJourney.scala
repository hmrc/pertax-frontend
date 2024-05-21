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

package testUtils.fakes

import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.ActivatedOnlineFilerSelfAssessmentUser
import play.api.mvc._
import play.api.test.Helpers.stubBodyParser
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.domain.SaUtr

import scala.concurrent.{ExecutionContext, Future}

class FakeAuthJourney extends AuthJourney {
  override val authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
    new ActionBuilder[UserRequest, AnyContent] {
      override def parser: BodyParser[AnyContent] = stubBodyParser()

      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(buildUserRequest(request = request, saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("0123456789"))))

      override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    }
}
