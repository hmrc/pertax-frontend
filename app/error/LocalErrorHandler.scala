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

import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import org.apache.pekko.stream.Materializer
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import play.twirl.api.Html
import uk.gov.hmrc.play.bootstrap.frontend.http.FrontendErrorHandler
import views.html.{InternalServerErrorView, UnauthenticatedErrorView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LocalErrorHandler @Inject() (
  val messagesApi: MessagesApi,
  val materializer: Materializer,
  internalServerErrorView: InternalServerErrorView,
  unauthenticatedErrorTemplate: UnauthenticatedErrorView
)(implicit val configDecorator: ConfigDecorator, val ec: ExecutionContext)
    extends FrontendErrorHandler
    with I18nSupport {

  override def standardErrorTemplate(
    pageTitle: String,
    heading: String,
    message: String
  )(implicit requestHeader: RequestHeader): Future[Html] = {
    val messages: Messages = messagesApi.preferred(requestHeader)
    val dummyRequest       = Request(requestHeader, AnyContentAsEmpty)
    Future.successful(unauthenticatedErrorTemplate(pageTitle, heading, message)(dummyRequest, messages))
  }

  override def internalServerErrorTemplate(implicit requestHeader: RequestHeader): Future[Html] = {
    val messages: Messages = messagesApi.preferred(requestHeader)
    val dummyRequest       = Request(requestHeader, AnyContentAsEmpty)
    Future.successful(internalServerErrorView()(dummyRequest, configDecorator, messages))
  }

}
