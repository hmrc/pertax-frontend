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

package controllers.support

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.PertaxBaseController
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents}
import views.html.support.UnderstandingYourAccountView

import scala.concurrent.Future

class SupportController @Inject(authJourney: AuthJourney)(cc: MessagesControllerComponents, understandingYourAccountView: UnderstandingYourAccountView)(implicit
                                                                                                                      configDecorator: ConfigDecorator
) extends PertaxBaseController(cc) {

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def understandingYourAccount: Action[AnyContent] = authenticate.async { implicit request =>
    Future.successful {
      Ok(understandingYourAccountView())
    }
  }
}
