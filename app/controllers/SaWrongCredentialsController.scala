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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.{SelfAssessmentUser, SelfAssessmentUserType}
import models.dto.SAWrongCredentialsDto
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever

class SaWrongCredentialsController @Inject()(val messagesApi: MessagesApi, authJourney: AuthJourney)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer)
    extends PertaxBaseController {
  private val authenticate: ActionBuilder[UserRequest] = authJourney.authWithSelfAssessment

  def landingPage: Action[AnyContent] = authenticate { implicit request =>
    Ok(views.html.selfassessment.signedInWrongAccount())
  }

  def doYouKnowOtherCredentials: Action[AnyContent] = authenticate { implicit request =>
    Ok(views.html.selfassessment.doYouKnowOtherCredentials(SAWrongCredentialsDto.form))
  }

  def signInAgain: Action[AnyContent] = authenticate { implicit request =>
    Ok(views.html.selfassessment.signInAgain())
  }

  def doYouKnowUserId: Action[AnyContent] = authenticate { implicit request =>
    Ok(views.html.selfassessment.doYouKnowUserId(SAWrongCredentialsDto.form))
  }

  private def getSaUtr(implicit request: UserRequest[AnyContent]) =
    request.saUserType match {
      case saUser: SelfAssessmentUser => Some(saUser.saUtr.utr)
      case _                          => None
    }

  def needToResetPassword: Action[AnyContent] = authenticate { implicit request =>
    Ok(views.html.selfassessment.needToResetPassword(getSaUtr))
  }

  def findYourUserId: Action[AnyContent] = authenticate { implicit request =>
    Ok(views.html.selfassessment.findYourUserId(getSaUtr))
  }

  def processDoYouKnowOtherCredentials: Action[AnyContent] = authenticate { implicit request =>
    SAWrongCredentialsDto.form.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.selfassessment.doYouKnowOtherCredentials(formWithErrors))
      },
      wrongCredentialsDto => {
        if (wrongCredentialsDto.value) {
          Redirect(routes.SaWrongCredentialsController.signInAgain())
        } else {
          Redirect(routes.SaWrongCredentialsController.doYouKnowUserId())
        }
      }
    )
  }

  def processDoYouKnowUserId: Action[AnyContent] = authenticate { implicit request =>
    SAWrongCredentialsDto.form.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.selfassessment.doYouKnowUserId(formWithErrors))
      },
      wrongCredentialsDto => {
        if (wrongCredentialsDto.value) {
          Redirect(routes.SaWrongCredentialsController.needToResetPassword())
        } else {
          Redirect(routes.SaWrongCredentialsController.findYourUserId())
        }
      }
    )
  }
}
