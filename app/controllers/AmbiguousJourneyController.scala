/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.Inject

import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.{AuthorisedActions, PertaxRegime}
import models.dto.AmbiguousUserFlowDto
import models.{AmbiguousFilerSelfAssessmentUser, PertaxContext}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, LocalSessionCache, SelfAssessmentService, UserDetailsService}
import uk.gov.hmrc.domain.SaUtr
import util.LocalPartialRetriever

import scala.concurrent.Future

class AmbiguousJourneyController @Inject() (
  val messagesApi: MessagesApi,
  val citizenDetailsService: CitizenDetailsService,
  val pertaxRegime: PertaxRegime,
  val auditConnector: PertaxAuditConnector,
  val partialRetriever: LocalPartialRetriever,
  val userDetailsService: UserDetailsService,
  val configDecorator: ConfigDecorator,
  val delegationConnector: FrontEndDelegationConnector,
  val sessionCache: LocalSessionCache,
  val authConnector: PertaxAuthConnector,
  val messageFrontendService: MessageFrontendService,
  val selfAssessmentService: SelfAssessmentService

) extends PertaxBaseController with AuthorisedActions {

  def enforceAmbiguousUser(block: SaUtr => Future[Result])(implicit context: PertaxContext): Future[Result] = {
    selfAssessmentService.getSelfAssessmentUserType(context.authContext) flatMap {
      case ambigUser: AmbiguousFilerSelfAssessmentUser => block(ambigUser.saUtr)
      case _ => Future.successful(Redirect(routes.ApplicationController.index()))
    }
  }

  def filedReturnOnlineChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    enforceAmbiguousUser {_ =>
      Future.successful(Ok(views.html.ambiguousjourney.filedReturnOnlineChoice(AmbiguousUserFlowDto.form)))
    }
  }

  def processFileReturnOnlineChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      AmbiguousUserFlowDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.ambiguousjourney.filedReturnOnlineChoice(formWithErrors)))
        },
        ambiguousFiledOnlineChoiceDto => {
          ambiguousFiledOnlineChoiceDto.value match {
            case true => Future.successful(Redirect(routes.AmbiguousJourneyController.deEnrolledFromSaChoice))
            case false => Future.successful(Redirect(routes.AmbiguousJourneyController.filedReturnByPostChoice))
          }
        }
      )
  }

  def deEnrolledFromSaChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    enforceAmbiguousUser {_ =>
      Future.successful(Ok(views.html.ambiguousjourney.deEnrolledFromSaChoice(AmbiguousUserFlowDto.form)))
    }
  }

  def processDeEnroleedFromSaChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      AmbiguousUserFlowDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.ambiguousjourney.deEnrolledFromSaChoice(formWithErrors)))
        },
        ambiguousFiledOnlineChoiceDto => {
          ambiguousFiledOnlineChoiceDto.value match {
            case true => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("need-to-enrol-again")))
            case false => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("need-to-use-created-creds")))
          }
        }
      )
  }

  def filedReturnByPostChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    enforceAmbiguousUser {_ =>
      Future.successful(Ok(views.html.ambiguousjourney.filedReturnByPostChoice(AmbiguousUserFlowDto.form)))
    }
  }

  def processFiledReturnByPostChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      AmbiguousUserFlowDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.ambiguousjourney.filedReturnByPostChoice(formWithErrors)))
        },
        ambiguousFiledOnlineChoiceDto => {
          ambiguousFiledOnlineChoiceDto.value match {
            case true => Future.successful(Redirect(routes.AmbiguousJourneyController.usedUtrToRegisterChoice()))
            case false => Future.successful(Redirect(routes.AmbiguousJourneyController.receivedUtrLetterChoice()))
          }
        }
      )
  }

  def usedUtrToRegisterChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    enforceAmbiguousUser {_ =>
      Future.successful(Ok(views.html.ambiguousjourney.usedUtrToRegisterChoice(AmbiguousUserFlowDto.form)))
    }
  }

  def processUsedUtrToRegisterChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      AmbiguousUserFlowDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.ambiguousjourney.usedUtrToRegisterChoice(formWithErrors)))
        },
        ambiguousFiledOnlineChoiceDto => {
          ambiguousFiledOnlineChoiceDto.value match {
            case true => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("pin-expired-register")))
            case false => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("deadline")))
          }
        }
      )
  }

  def receivedUtrLetterChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    enforceAmbiguousUser { _ =>
      Future.successful(Ok(views.html.ambiguousjourney.receivedUtrLetterChoice(AmbiguousUserFlowDto.form)))
    }
  }

  def processReceivedUtrLetterChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      AmbiguousUserFlowDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.ambiguousjourney.receivedUtrLetterChoice(formWithErrors)))
        },
        ambiguousFiledOnlineChoiceDto => {
          ambiguousFiledOnlineChoiceDto.value match {
            case true => Future.successful(Redirect(routes.AmbiguousJourneyController.usedUtrToEnrolChoice()))
            case false => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("letter-in-post")))
          }
        }
      )
  }

  def usedUtrToEnrolChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    enforceAmbiguousUser { _ =>
      Future.successful(Ok(views.html.ambiguousjourney.usedUtrToEnrolChoice(AmbiguousUserFlowDto.form)))
    }
  }

  def processUsedUtrToEnrolChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      AmbiguousUserFlowDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.ambiguousjourney.usedUtrToEnrolChoice(formWithErrors)))
        },
        ambiguousFiledOnlineChoiceDto => {
          ambiguousFiledOnlineChoiceDto.value match {
            case true => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("pin-expired-enrol")))
            case false => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("need-to-enrol")))
          }
        }
      )
  }

  def handleAmbiguousJourneyLandingPages(page: String): Action[AnyContent] = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>

      val continueUrl = controllers.routes.ApplicationController.index().url
      enforceAmbiguousUser { saUtr =>
        Future.successful {
          page match {
            case "need-to-enrol" => Ok(views.html.ambiguousjourney.youNeedToEnrol(saUtr, continueUrl))
            case "need-to-enrol-again" => Ok(views.html.ambiguousjourney.youNeedToEnrolAgain(saUtr, continueUrl))
            case "need-to-use-created-creds" => Ok(views.html.ambiguousjourney.youNeedToUseCreatedCreds(saUtr, continueUrl))
            case "deadline" => Ok(views.html.ambiguousjourney.deadlineIs(saUtr, continueUrl))
            case "letter-in-post" => Ok(views.html.ambiguousjourney.letterMayBeInPost(saUtr, continueUrl))
            case "pin-expired-enrol" => Ok(views.html.ambiguousjourney.pinExpired(saUtr, continueUrl, routes.AmbiguousJourneyController.usedUtrToEnrolChoice()))
            case "pin-expired-register" => Ok(views.html.ambiguousjourney.pinExpired(saUtr, continueUrl, routes.AmbiguousJourneyController.usedUtrToRegisterChoice()))
            case _ => Ok(views.html.selfAssessmentNotShown(saUtr))
          }
        }
      }
    }
}
