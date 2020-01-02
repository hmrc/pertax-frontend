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

import akka.stream.Materializer
import config.ConfigDecorator
import controllers.auth.AuthJourney
import com.google.inject.{Inject, Singleton}
import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever

import scala.concurrent.Future

@Singleton
class LocalErrorHandler @Inject()(
  val messagesApi: MessagesApi,
  val materializer: Materializer,
  authJourney: AuthJourney
)(
  implicit val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val templateRenderer: TemplateRenderer)
    extends HttpErrorHandler with I18nSupport with RendersErrors {

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    if (statusCode == BAD_REQUEST || statusCode == NOT_FOUND) {
      authJourney.authWithPersonalDetails
        .async { implicit request =>
          futureError(statusCode)
        }
        .apply(request)
        .run()(materializer)
    } else {
      Action
        .async { implicit request =>
          unauthenticatedFutureError(statusCode)
        }
        .apply(request)
        .run()(materializer)
    }

  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    onClientError(request, INTERNAL_SERVER_ERROR, "")

}
