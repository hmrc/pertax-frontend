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
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, LocalSessionCache, UserDetailsService}
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
  val messageFrontendService: MessageFrontendService

) extends PertaxBaseController with AuthorisedActions {

  def filedReturnOnlineChoice = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    Future.successful(Ok(views.html.ambiguousjourney.filedReturnOnlineChoice(AmbiguousUserFlowDto.form)))
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
    Future.successful(Ok(views.html.ambiguousjourney.deEnrolledFromSaChoice(AmbiguousUserFlowDto.form)))
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
    Future.successful(Ok(views.html.ambiguousjourney.filedReturnByPostChoice(AmbiguousUserFlowDto.form)))
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
    Future.successful(Ok(views.html.ambiguousjourney.usedUtrToRegisterChoice(AmbiguousUserFlowDto.form)))
  }

  def processUsedUtrToRegisterChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      AmbiguousUserFlowDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.ambiguousjourney.usedUtrToRegisterChoice(formWithErrors)))
        },
        ambiguousFiledOnlineChoiceDto => {
          ambiguousFiledOnlineChoiceDto.value match {
            case true => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("pin-expired")))
            case false => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("deadline")))
          }
        }
      )
  }

  def receivedUtrLetterChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    Future.successful(Ok(views.html.ambiguousjourney.receivedUtrLetterChoice(AmbiguousUserFlowDto.form)))
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
    Future.successful(Ok(views.html.ambiguousjourney.usedUtrToEnrolChoice(AmbiguousUserFlowDto.form)))
  }

  def processUsedUtrToEnrolChoice: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      AmbiguousUserFlowDto.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.ambiguousjourney.usedUtrToEnrolChoice(formWithErrors)))
        },
        ambiguousFiledOnlineChoiceDto => {
          ambiguousFiledOnlineChoiceDto.value match {
            case true => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("pin-expired")))
            case false => Future.successful(Redirect(routes.AmbiguousJourneyController.handleAmbiguousJourneyLandingPages("need-to-enrol")))
          }
        }
      )
  }

  def handleAmbiguousJourneyLandingPages(page: String): Action[AnyContent] = VerifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    val utr = pertaxContext.user.flatMap(_.saUtr)
    val continueUrl = controllers.routes.ApplicationController.index().url

    page match {
      case "need-to-enrol" => Future.successful(Ok(views.html.ambiguousjourney.youNeedToEnrol(utr, continueUrl)))
      case "need-to-enrol-again" => Future.successful(Ok(views.html.ambiguousjourney.youNeedToEnrolAgain(utr, continueUrl)))
      case "need-to-use-created-creds" => Future.successful(Ok(views.html.ambiguousjourney.youNeedToUseCreatedCreds(utr, continueUrl)))
      case "deadline" => Future.successful(Ok(views.html.ambiguousjourney.deadlineIs(utr, continueUrl)))
      case "letter-in-post" => Future.successful(Ok(views.html.ambiguousjourney.letterMayBeInPost(utr)))
      case "pin-expired" => Future.successful(Ok(views.html.ambiguousjourney.pinExpired(utr, continueUrl)))
      case _ => Future.successful(Redirect(routes.ApplicationController.handleSelfAssessment))
    }
  }
}
