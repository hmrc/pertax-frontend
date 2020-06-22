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

package controllers.auth

import com.google.inject.Inject
import config.ConfigDecorator
import play.api.mvc.Results.Redirect
import play.api.mvc._
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class WithActiveSession @Inject()(cc: ControllerComponents, configDecorator: ConfigDecorator)(
  implicit ec: ExecutionContext)
    extends ActionBuilder[Request, AnyContent] {

  override protected def executionContext: ExecutionContext = cc.executionContext
  override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser
  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    val hc =
      HeaderCarrierConverter.fromHeadersAndSessionAndRequest(request.headers, Some(request.session), Some(request))

    hc.sessionId.fold(Future.successful(Redirect(configDecorator.authProviderChoice)))(_ => block(request))
  }
}
