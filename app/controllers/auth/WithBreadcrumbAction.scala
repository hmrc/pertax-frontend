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

import com.google.inject.Inject
import controllers.auth.requests.UserRequest
import models.Breadcrumb
import play.api.mvc.{ActionRefiner, Result}

import scala.concurrent.{ExecutionContext, Future}

class WithBreadcrumbAction @Inject() (implicit ec: ExecutionContext) {

  def addBreadcrumb(breadcrumb: Breadcrumb): ActionRefiner[UserRequest, UserRequest] =
    new ActionRefiner[UserRequest, UserRequest] {
      override protected def refine[A](request: UserRequest[A]): Future[Either[Result, UserRequest[A]]] =
        Future.successful(
          Right(
            UserRequest(
              request.authNino,
              request.retrievedName,
              request.saUserType,
              request.credentials,
              request.confidenceLevel,
              request.trustedHelper,
              request.enrolments,
              request.profile,
              Some(breadcrumb),
              request.request,
              request.userAnswers
            )
          )
        )

      override protected def executionContext: ExecutionContext = ec
    }

}
