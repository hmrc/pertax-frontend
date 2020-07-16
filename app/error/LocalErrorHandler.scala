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
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import play.twirl.api.Html
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever

@Singleton
class LocalErrorHandler @Inject()(val messagesApi: MessagesApi, val materializer: Materializer)(
  implicit val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val templateRenderer: TemplateRenderer)
    extends FrontendErrorHandler with I18nSupport {

  override def standardErrorTemplate(
    pageTitle: String,
    heading: String,
    message: String
  )(implicit request: Request[_]): Html =
    views.html.unauthenticatedError(pageTitle, Some(heading), Some(message))

}
