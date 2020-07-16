/*
 * Copyright 2020 HM Revenue & Customs
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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import play.api.Play
import play.api.i18n.Messages
import play.api.mvc.Result
import play.api.mvc.Results.{BadRequest, InternalServerError}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import views.html.ErrorView

object GenericErrors {

  implicit val templateRenderer: TemplateRenderer = Play.current.injector.instanceOf[TemplateRenderer]
  val errorView: ErrorView = Play.current.injector.instanceOf[ErrorView]

  def badRequest(
    implicit request: UserRequest[_],
    configDecorator: ConfigDecorator,
    partialRetriever: LocalPartialRetriever,
    messages: Messages): Result =
    BadRequest(
      errorView(
        "global.error.BadRequest.title",
        Some("global.error.BadRequest.title"),
        List("global.error.BadRequest.message1", "global.error.BadRequest.message2")))

  def internalServerError(
    implicit request: UserRequest[_],
    configDecorator: ConfigDecorator,
    partialRetriever: LocalPartialRetriever,
    messages: Messages): Result =
    InternalServerError(
      errorView(
        "global.error.InternalServerError500.title",
        Some("global.error.InternalServerError500.title"),
        List("global.error.InternalServerError500.message")))
}
