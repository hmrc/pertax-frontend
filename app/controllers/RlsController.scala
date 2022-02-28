/*
 * Copyright 2022 HM Revenue & Customs
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
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import models.Address
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.renderer.{ActiveTabHome, TemplateRenderer}
import views.html.InternalServerErrorView
import views.html.personaldetails.CheckYourAddressInterruptView

import scala.concurrent.{ExecutionContext, Future}

class RlsController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  checkYourAddressInterruptView: CheckYourAddressInterruptView,
  internalServerErrorView: InternalServerErrorView
)(implicit
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext
) extends PertaxBaseController(cc) {

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails
  def rlsInterruptOnPageLoad(): Action[AnyContent] = authenticate.async { implicit request =>
    request.personDetails
      .map { personDetails =>
        val mainAddress = personDetails.address.flatMap(address => if (address.isRls) Some(address) else None)
        val correspondenceAddress =
          personDetails.correspondenceAddress.flatMap(address => if (address.isRls) Some(address) else None)
        Future.successful(Ok(checkYourAddressInterruptView(mainAddress, correspondenceAddress)))
      }
      .getOrElse(Future.successful(InternalServerError(internalServerErrorView())))
  }
}
