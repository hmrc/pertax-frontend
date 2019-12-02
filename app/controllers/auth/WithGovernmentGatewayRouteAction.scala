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

import java.net.URLEncoder

import controllers.auth.requests.UserRequest
import controllers.routes
import play.api.mvc.{ActionRefiner, ActionTransformer, Result}

import scala.concurrent.Future

class WithGovernmentGatewayRouteAction extends ActionTransformer[UserRequest, UserRequest] {

  override def transform[A](request: UserRequest[A]): Future[UserRequest[A]] =
    Future.successful(
      UserRequest(
        request.nino,
        request.retrievedName,
        request.previousLoginTime,
        request.saUserType,
        request.credentials,
        request.confidenceLevel,
        request.personDetails,
        request.trustedHelper,
        request.profile.fold(Option.empty[String])(v =>
          Some(v + "?redirect_uri=" + URLEncoder
            .encode(routes.HomeController.index().absoluteURL()(request), "UTF-8"))),
        request.unreadMessageCount,
        request.activeTab,
        request.breadcrumb,
        request.request
      ))
}
