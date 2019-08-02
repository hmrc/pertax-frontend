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

import config.ConfigDecorator
import models.PertaxContext
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.play.frontend.auth.DelegationAwareActions
import uk.gov.hmrc.play.frontend.controller.UnauthorisedAction
import util.LocalPartialRetriever

import scala.concurrent.Future

trait PublicActions extends DelegationAwareActions {

  def partialRetriever: LocalPartialRetriever
  def configDecorator: ConfigDecorator

  def PublicAction(block: PertaxContext => Future[Result]): Action[AnyContent] =
    UnauthorisedAction.async { implicit request =>
      trimmingFormUrlEncodedData { implicit request =>
        block(PertaxContext(request, partialRetriever, configDecorator))
      }
    }

  def trimmingFormUrlEncodedData(block: Request[AnyContent] => Future[Result])(
    implicit request: Request[AnyContent]): Future[Result] =
    block {
      request.map {
        case AnyContentAsFormUrlEncoded(data) =>
          AnyContentAsFormUrlEncoded(data.map {
            case (key, vals) => (key, vals.map(_.trim))
          })
        case b => b
      }
    }
}
