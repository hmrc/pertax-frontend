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

package controllers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, EnforceAmbiguousUserAction}
import com.google.inject.Inject
import models.{NotEnrolledSelfAssessmentUser, SelfAssessmentUser}
import models.dto.AmbiguousUserFlowDto
import org.joda.time.DateTime
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.{DateTimeTools, LocalPartialRetriever, TaxYearRetriever}

import scala.concurrent.Future

class AmbiguousJourneyController @Inject()(
  val messagesApi: MessagesApi,
  val taxYearRetriever: TaxYearRetriever,
  authJourney: AuthJourney,
  enforceAmbiguousUserAction: EnforceAmbiguousUserAction,
  dateTimeTools: DateTimeTools)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer)
    extends PertaxBaseController with CurrentTaxYear {

  override def now: () => DateTime = () => DateTime.now()

  private val authenticate: ActionBuilder[UserRequest] = authJourney.authWithPersonalDetails

  private def getSaUtr(implicit request: UserRequest[AnyContent]): Option[SaUtr] =
    request.saUserType match {
      case saUser: SelfAssessmentUser => Some(saUser.saUtr)
      case _                          => None
    }

  def landingPage: Action[AnyContent] = (authenticate andThen enforceAmbiguousUserAction).async { implicit request =>
    Future.successful(Ok(views.html.selfAssessmentNotShown(getSaUtr)))
  }

  def filedReturnOnlineChoice: Action[AnyContent] = (authenticate andThen enforceAmbiguousUserAction).async {
    implicit request =>
      Future.successful(Ok(views.html.ambiguousjourney.filedReturnOnlineChoice(AmbiguousUserFlowDto.form)))
  }

  def processFileReturnOnlineChoice: Action[AnyContent] = authenticate { implicit request =>
    AmbiguousUserFlowDto.form.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.ambiguousjourney.filedReturnOnlineChoice(formWithErrors))
      },
      ambiguousFiledOnlineChoiceDto => {
        if (ambiguousFiledOnlineChoiceDto.value) {
          Redirect(routes.AmbiguousJourneyController.deEnrolledFromSaChoice())
        } else {
          if (configDecorator.saAmbigSimplifiedJourneyEnabled) {
            Redirect(routes.AmbiguousJourneyController.usedUtrToEnrolChoice())
          } else {
            Redirect(routes.AmbiguousJourneyController.filedReturnByPostChoice())
          }
        }
      }
    )
  }

  def deEnrolledFromSaChoice: Action[AnyContent] = (authenticate andThen enforceAmbiguousUserAction) {
    implicit request =>
      Ok(views.html.ambiguousjourney.deEnrolledFromSaChoice(AmbiguousUserFlowDto.form))
  }

  def processDeEnroledFromSaChoice: Action[AnyContent] = authenticate { implicit request =>
    AmbiguousUserFlowDto.form.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.ambiguousjourney.deEnrolledFromSaChoice(formWithErrors))
      },
      ambiguousFiledOnlineChoiceDto => {
        if (ambiguousFiledOnlineChoiceDto.value) {
          Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("need-to-enrol-again"))
        } else {
          Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("need-to-use-created-creds"))
        }
      }
    )
  }

  def filedReturnByPostChoice: Action[AnyContent] = (authenticate andThen enforceAmbiguousUserAction) {
    implicit request =>
      Ok(views.html.ambiguousjourney.filedReturnByPostChoice(AmbiguousUserFlowDto.form))
  }

  def processFiledReturnByPostChoice: Action[AnyContent] = authenticate { implicit request =>
    AmbiguousUserFlowDto.form.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.ambiguousjourney.filedReturnByPostChoice(formWithErrors))
      },
      ambiguousFiledOnlineChoiceDto => {
        if (ambiguousFiledOnlineChoiceDto.value) {
          Redirect(routes.AmbiguousJourneyController.usedUtrToRegisterChoice())
        } else {
          if (configDecorator.saAmbigSkipUTRLetterEnabled) {
            Redirect(routes.AmbiguousJourneyController.usedUtrToEnrolChoice())
          } else {
            Redirect(routes.AmbiguousJourneyController.receivedUtrLetterChoice())
          }
        }
      }
    )
  }

  def usedUtrToRegisterChoice: Action[AnyContent] = (authenticate andThen enforceAmbiguousUserAction) {
    implicit request =>
      Ok(
        views.html.ambiguousjourney.usedUtrToEnrolChoice(
          AmbiguousUserFlowDto.form,
          routes.AmbiguousJourneyController.filedReturnByPostChoice().url))
  }

  def processUsedUtrToRegisterChoice: Action[AnyContent] = authenticate { implicit request =>
    AmbiguousUserFlowDto.form.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(
          views.html.ambiguousjourney
            .usedUtrToEnrolChoice(formWithErrors, routes.AmbiguousJourneyController.filedReturnByPostChoice().url))
      },
      ambiguousFiledOnlineChoiceDto => {
        if (ambiguousFiledOnlineChoiceDto.value) {
          Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("pin-expired-register"))
        } else {
          Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("deadline"))
        }
      }
    )
  }

  def receivedUtrLetterChoice: Action[AnyContent] = (authenticate andThen enforceAmbiguousUserAction) {
    implicit request =>
      Ok(views.html.ambiguousjourney.receivedUtrLetterChoice(AmbiguousUserFlowDto.form))
  }

  def processReceivedUtrLetterChoice: Action[AnyContent] = authenticate { implicit request =>
    AmbiguousUserFlowDto.form.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.ambiguousjourney.receivedUtrLetterChoice(formWithErrors))
      },
      ambiguousFiledOnlineChoiceDto => {
        if (ambiguousFiledOnlineChoiceDto.value) {
          Redirect(routes.AmbiguousJourneyController.usedUtrToEnrolChoice())
        } else {
          Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("letter-in-post"))
        }
      }
    )
  }

  def usedUtrToEnrolBackLink(): String =
    (configDecorator.saAmbigSimplifiedJourneyEnabled, configDecorator.saAmbigSkipUTRLetterEnabled) match {
      case (true, _)      => controllers.routes.AmbiguousJourneyController.filedReturnOnlineChoice().url
      case (false, true)  => controllers.routes.AmbiguousJourneyController.processFiledReturnByPostChoice().url
      case (false, false) => controllers.routes.AmbiguousJourneyController.receivedUtrLetterChoice().url
    }

  def usedUtrToEnrolChoice: Action[AnyContent] = (authenticate andThen enforceAmbiguousUserAction) { implicit request =>
    Ok(views.html.ambiguousjourney.usedUtrToEnrolChoice(AmbiguousUserFlowDto.form, usedUtrToEnrolBackLink()))
  }

  def processUsedUtrToEnrolChoice: Action[AnyContent] = authenticate { implicit request =>
    AmbiguousUserFlowDto.form.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.ambiguousjourney.usedUtrToEnrolChoice(formWithErrors, usedUtrToEnrolBackLink()))
      },
      ambiguousFiledOnlineChoiceDto => {
        if (ambiguousFiledOnlineChoiceDto.value) {
          Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("wrong-account"))
        } else {
          Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("need-to-enrol"))
        }
      }
    )
  }

  def handleAmbiguousJourneyLandingPages(page: String): Action[AnyContent] =
    (authenticate andThen enforceAmbiguousUserAction) { implicit request =>
      val continueUrl = controllers.routes.HomeController.index().url
      request.saUserType match {
        case NotEnrolledSelfAssessmentUser(saUtr) =>
          val currentTaxYear = taxYearRetriever.currentYear
          val deadlineYear = currentTaxYear + 1
          val showSendTaxReturnByPost = dateTimeTools.showSendTaxReturnByPost

          page match {
            case "need-to-enrol" =>
              Ok(
                views.html.ambiguousjourney.youNeedToEnrol(
                  saUtr,
                  continueUrl,
                  deadlineYear.toString,
                  currentTaxYear.toString,
                  showSendTaxReturnByPost))
            case "need-to-enrol-again" =>
              Ok(
                views.html.ambiguousjourney.youNeedToEnrolAgain(
                  saUtr,
                  continueUrl,
                  deadlineYear.toString,
                  currentTaxYear.toString,
                  showSendTaxReturnByPost))
            case "need-to-use-created-creds" =>
              Ok(views.html.ambiguousjourney.youNeedToUseCreatedCreds(saUtr, continueUrl))
            case "deadline"       => Ok(views.html.ambiguousjourney.deadlineIs(saUtr, continueUrl))
            case "letter-in-post" => Ok(views.html.ambiguousjourney.letterMayBeInPost(saUtr, continueUrl))
            case "wrong-account" =>
              Ok(
                views.html.ambiguousjourney
                  .wrongAccount(saUtr, continueUrl, routes.AmbiguousJourneyController.usedUtrToEnrolChoice()))
            case "pin-expired-register" =>
              Ok(
                views.html.ambiguousjourney
                  .wrongAccount(saUtr, continueUrl, routes.AmbiguousJourneyController.usedUtrToRegisterChoice()))
            case _ => Ok(views.html.selfAssessmentNotShown(Some(saUtr)))
          }
        case _ => Redirect(routes.HomeController.index())
      }
    }
}
