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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import io.lemonlabs.uri.{QueryString, Url}
import models.SelfAssessmentUser
import models.dto.SAWrongCredentialsDto
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents}
import views.html.selfassessment._

class SaWrongCredentialsController @Inject() (
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  signedInWrongAccountView: SignedInWrongAccountView,
  doYouKnowOtherCredentialsView: DoYouKnowOtherCredentialsView,
  signInAgainView: SignInAgainView,
  doYouKnowUserIdView: DoYouKnowUserIdView,
  needToResetPasswordView: NeedToResetPasswordView,
  findYourUserIdView: FindYourUserIdView
)(implicit configDecorator: ConfigDecorator)
    extends PertaxBaseController(cc) {

  private val authenticate: ActionBuilder[UserRequest, AnyContent] = authJourney.authWithPersonalDetails

  def ggSignInUrl: String = {
    lazy val ggSignIn = s"${configDecorator.basGatewayFrontendHost}/bas-gateway/sign-in"

    val continueUrl = configDecorator.pertaxFrontendHost + configDecorator.personalAccount

    Url(
      path = ggSignIn,
      query = QueryString.fromPairs(
        "continue_url" -> continueUrl,
        "accountType"  -> "individual",
        "origin"       -> configDecorator.defaultOrigin.origin
      )
    ).toString()
  }

  def landingPage(): Action[AnyContent] = authenticate { implicit request =>
    Ok(signedInWrongAccountView())
  }

  def doYouKnowOtherCredentials(): Action[AnyContent] = authenticate { implicit request =>
    Ok(doYouKnowOtherCredentialsView(SAWrongCredentialsDto.form))
  }

  def signInAgain(): Action[AnyContent] = authenticate { implicit request =>
    Ok(signInAgainView(ggSignInUrl))
  }

  def doYouKnowUserId(): Action[AnyContent] = authenticate { implicit request =>
    Ok(doYouKnowUserIdView(SAWrongCredentialsDto.form))
  }

  private def getSaUtr(implicit request: UserRequest[AnyContent]) =
    request.saUserType match {
      case saUser: SelfAssessmentUser => Some(saUser.saUtr.utr)
      case _                          => None
    }

  def needToResetPassword(): Action[AnyContent] = authenticate { implicit request =>
    Ok(needToResetPasswordView(getSaUtr, ggSignInUrl))
  }

  def findYourUserId(): Action[AnyContent] = authenticate { implicit request =>
    Ok(findYourUserIdView(getSaUtr, ggSignInUrl))
  }

  def processDoYouKnowOtherCredentials(): Action[AnyContent] = authenticate { implicit request =>
    SAWrongCredentialsDto.form
      .bindFromRequest()
      .fold(
        formWithErrors => BadRequest(doYouKnowOtherCredentialsView(formWithErrors)),
        wrongCredentialsDto =>
          if (wrongCredentialsDto.value) {
            Redirect(routes.SaWrongCredentialsController.signInAgain())
          } else {
            Redirect(routes.SaWrongCredentialsController.doYouKnowUserId())
          }
      )
  }

  def processDoYouKnowUserId(): Action[AnyContent] = authenticate { implicit request =>
    SAWrongCredentialsDto.form
      .bindFromRequest()
      .fold(
        formWithErrors => BadRequest(doYouKnowUserIdView(formWithErrors)),
        wrongCredentialsDto =>
          if (wrongCredentialsDto.value) {
            Redirect(routes.SaWrongCredentialsController.needToResetPassword())
          } else {
            Redirect(routes.SaWrongCredentialsController.findYourUserId())
          }
      )
  }
}
