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
import controllers.auth.requests.RefinedRequest
import controllers.routes
import models.AmbiguousFilerSelfAssessmentUser
import play.api.mvc.Results._
import play.api.mvc.{ActionFunction, ActionRefiner, Result}

import scala.concurrent.Future

class EnforceAmbiguousUserAction
    extends ActionRefiner[RefinedRequest, RefinedRequest] with ActionFunction[RefinedRequest, RefinedRequest] {

  override protected def refine[A](request: RefinedRequest[A]): Future[Either[Result, RefinedRequest[A]]] =
    request.saUserType match {
      case _: AmbiguousFilerSelfAssessmentUser => Future.successful(Right(request))
      case _                                   => Future.successful(Left(Redirect(routes.HomeController.index())))
    }

}
