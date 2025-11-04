/*
 * Copyright 2025 HM Revenue & Customs
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

package controllers

import com.google.inject.Inject
import controllers.auth.requests.UserRequest
import controllers.auth.AuthJourney
import play.api.Logging
import play.api.mvc.*
import scala.concurrent.Future
import views.html.personaldetails.TryAgainToUpdateDetailsView

class UpdateDetailsErrorController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  tryAgainToUpdateDetailsView: TryAgainToUpdateDetailsView
) extends PertaxBaseController(cc)
    with Logging {

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def displayTryAgainToUpdateDetails: Action[AnyContent] = authenticate.async { implicit request =>
    Future.successful(Conflict(tryAgainToUpdateDetailsView()))
  }

}
