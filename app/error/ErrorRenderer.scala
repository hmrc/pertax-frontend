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

package error

import com.google.inject.Inject
import controllers.auth.requests.UserRequest
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND}
import play.api.i18n.Messages
import play.api.mvc._
import views.html.{ErrorView, NotFoundView, UnauthenticatedErrorView}

import scala.concurrent.Future

class ErrorRenderer @Inject() (
  notFoundView: NotFoundView,
  errorView: ErrorView,
  unauthenticatedErrorTemplate: UnauthenticatedErrorView
) extends Results {

  def futureError(statusCode: Int)(implicit request: UserRequest[_], messages: Messages): Future[Result] =
    Future.successful(error(statusCode))

  def error(statusCode: Int)(implicit request: UserRequest[_], messages: Messages): Result = {

    val errorKey = statusCode match {
      case BAD_REQUEST => "badRequest400"
      case NOT_FOUND   => "pageNotFound404"
      case _           => "InternalServerError500"
    }
    Status(statusCode)(
      errorView(
        s"global.error.$errorKey.title",
        Some(s"global.error.$errorKey.heading"),
        List(s"global.error.$errorKey.message")
      )
    )
  }

  def unauthenticatedFutureError(statusCode: Int)(implicit request: Request[_], messages: Messages): Future[Result] =
    Future.successful(unauthenticatedError(statusCode))

  def unauthenticatedError(statusCode: Int)(implicit request: Request[_], messages: Messages): Result = {

    val errorKey = statusCode match {
      case BAD_REQUEST => "badRequest400"
      case NOT_FOUND   => "pageNotFound404"
      case _           => "InternalServerError500"
    }

    Status(statusCode)(
      unauthenticatedErrorTemplate(
        s"global.error.$errorKey.title",
        s"global.error.$errorKey.heading",
        s"global.error.$errorKey.message"
      )
    )

  }

  def notFoundFutureError(implicit request: UserRequest[_], messages: Messages): Future[Result] =
    Future.successful(NotFound(notFoundView()))

}
