/*
 * Copyright 2017 HM Revenue & Customs
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

import config.{ConfigDecorator, StaticGlobalDependencies}
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.{AuthorisedActions, LocalPageVisibilityPredicateFactory, PertaxRegime}
import controllers.bindable.Origin
import controllers.helpers.PaperlessInterruptHelper
import error.LocalErrorHandler
import models._
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc._
import services._
import services.partials.{CspPartialService, MessagePartialService}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.time.TaxYearResolver
import util.AuditServiceTools._
import util.{DateTimeTools, LocalPartialRetriever}

import scala.concurrent.Future


class ApplicationController @Inject() (
  val messagesApi: MessagesApi,
  val citizenDetailsService: CitizenDetailsService,
  val preferencesFrontendService: PreferencesFrontendService,
  val partialService: MessagePartialService,
  val taiService: TaiService,
  val identityVerificationFrontendService: IdentityVerificationFrontendService,
  val taxCalculationService: TaxCalculationService,
  val selfAssessmentService: SelfAssessmentService,
  val lifetimeAllowanceService: LifetimeAllowanceService,
  val cspPartialService: CspPartialService,
  val userDetailsService: UserDetailsService,
  val delegationConnector: FrontEndDelegationConnector,
  val auditConnector: PertaxAuditConnector,
  val authConnector: PertaxAuthConnector,
  val localPageVisibilityPredicateFactory: LocalPageVisibilityPredicateFactory,
  val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val pertaxRegime: PertaxRegime,
  val localErrorHandler: LocalErrorHandler
) extends PertaxBaseController with AuthorisedActions with PaperlessInterruptHelper {

  def index: Action[AnyContent] = ProtectedAction(Nil) {
    implicit pertaxContext =>

      val context: AuthContextDecorator = new AuthContextDecorator(pertaxContext.user.map(_.authContext))

      val year = TaxYearResolver.currentTaxYear

      val userAndNino = for( u <- pertaxContext.user; n <- u.nino) yield (u, n)

      val serviceCallResponses = userAndNino.fold[Future[(Option[TaxSummary],Option[TaxCalculation], Boolean)]](Future.successful( (None, None, false) )) { userAndNino =>

        val (user, nino) = userAndNino

        val taxCalculation: Future[Option[TaxCalculation]] = if (configDecorator.taxcalcEnabled) {
          taxCalculationService.getTaxCalculation(nino, year - 1) map {
            case TaxCalculationSuccessResponse(taxCalc) => Some(taxCalc)
            case _ => None
          }
        }
        else {
          Future.successful(None)
        }

        val taxSummary: Future[Option[TaxSummary]] = if (configDecorator.taxSummaryEnabled) {
          taiService.taxSummary(nino, year) map {
            case TaxSummarySuccessResponse(ts) => Some(ts)
            case _ => None
          }
        }
        else {
          Future.successful(None)
        }

        val showLtaSection: Future[Boolean] = if (configDecorator.ltaEnabled) {
          lifetimeAllowanceService.hasLtaProtection(nino)
        }
        else {
          Future.successful(false)
        }

        for {
         taxCalculation <- taxCalculation
         taxSummary <- taxSummary
         showLtaSection <- showLtaSection
        } yield (taxSummary, taxCalculation, showLtaSection)
      }

      val saActionNeeded = selfAssessmentService.getSelfAssessmentActionNeeded(pertaxContext.authContext)

      val messageInboxLinkPartial = partialService.getMessageInboxLinkPartial

      enforcePaperlessPreference {
        for {
          (taxSummary, taxCalculation, showLtaSection) <- serviceCallResponses
          inboxLinkPartial <- messageInboxLinkPartial
          saActionNeeded <- saActionNeeded
        } yield {
          Ok(views.html.home(
            inboxLinkPartial = inboxLinkPartial.successfulContentOrEmpty,
            showMarriageAllowanceSection = taxSummary.map(!_.isMarriageAllowanceRecipient).getOrElse(true),
            isActivePaye = taxSummary.isDefined,
            showCompanyBenefitSection = taxSummary.map(_.isCompanyBenefitRecipient).getOrElse(false),
            taxCalculationState = TaxCalculationState.buildFromTaxCalculation(taxCalculation),
            saActionNeeded = saActionNeeded,
            showLtaSection = showLtaSection,
            userResearchLinkUrl = configDecorator.urLinkUrl
          ))
        }
      }
  }

  def uplift(redirectUrl: Option[String]): Action[AnyContent] = {

    val pvp = if(redirectUrl.fold(false)(_.containsSlice("tax-credits-summary")))
      localPageVisibilityPredicateFactory.build(redirectUrl, Origin("PTA-TCS"))  //FIXME, this needs injected for tests
    else
      localPageVisibilityPredicateFactory.build(redirectUrl, configDecorator.defaultOrigin)  //FIXME, this needs injected for tests

    AuthorisedFor(pertaxRegime, pageVisibility = pvp).async {
      implicit authContext => implicit request =>
        Future.successful(Redirect(redirectUrl.getOrElse(routes.ApplicationController.index().url)))
    }
  }

  def showUpliftJourneyOutcome(continueUrl: Option[String]): Action[AnyContent] = AuthorisedAction() {
    implicit pertaxContext =>

      import IdentityVerificationSuccessResponse._

      //Will be populated if we arrived here because of an IV success/failure
      val journeyId = List(pertaxContext.request.getQueryString("token"), pertaxContext.request.getQueryString("journeyId")).flatten.headOption

      val highCreds = pertaxContext.user.map(_.hasHighCredStrength).getOrElse(false)

      val retryUrl = controllers.routes.ApplicationController.uplift(continueUrl).url

      lazy val allowContinue = configDecorator.allowSaPreview && pertaxContext.user.map(_.isSa).getOrElse(false)

      if (configDecorator.allowLowConfidenceSAEnabled)
        Future.successful(Redirect(controllers.routes.ApplicationController.ivExemptLandingPage(continueUrl)))
      else {
        journeyId match {
          case Some(jid) =>
            identityVerificationFrontendService.getIVJourneyStatus(jid).map { response =>

              (highCreds, response) match {
                case (_, IdentityVerificationSuccessResponse(InsufficientEvidence)) => Redirect(controllers.routes.ApplicationController.ivExemptLandingPage(continueUrl))
                case (_, IdentityVerificationSuccessResponse(UserAborted)) => Unauthorized(views.html.iv.failure.userAbortedOrIncomplete(retryUrl, allowContinue))
                case (_, IdentityVerificationSuccessResponse(FailedMatching)) => Unauthorized(views.html.iv.failure.failedMatching(retryUrl))
                case (_, IdentityVerificationSuccessResponse(LockedOut)) => Unauthorized(views.html.iv.failure.lockedOut(allowContinue))
                case (_, IdentityVerificationSuccessResponse(Incomplete)) => Unauthorized(views.html.iv.failure.userAbortedOrIncomplete(retryUrl, allowContinue))
                case (_, IdentityVerificationSuccessResponse(PrecondFailed)) => Unauthorized(views.html.iv.failure.cantConfirmIdentity(retryUrl, allowContinue))
                case (false, IdentityVerificationSuccessResponse(Success)) => Unauthorized(views.html.iv.failure.twoFaFailIvSuccess())
                case (true, IdentityVerificationSuccessResponse(Success)) => Ok(views.html.iv.success.success(continueUrl.getOrElse(routes.ApplicationController.index().url)))
                case (_, IdentityVerificationSuccessResponse(TechnicalIssue)) =>
                  Logger.warn(s"TechnicalIssue response from identityVerificationFrontendService")
                  InternalServerError(views.html.iv.failure.technicalIssues(retryUrl))
                case (c, r) =>
                  Logger.error(s"Unhandled response from identityVerificationFrontendService: $r")
                  InternalServerError(views.html.iv.failure.technicalIssues(retryUrl))
              }

            }
          case None =>
            // No journeyId signifies subsequent 2FA failure
            Future.successful(Unauthorized(views.html.iv.failure.youNeed2Fa(retryUrl, allowContinue)))
        }
      }
  }

  def signout(continueUrl: Option[String], origin: Option[Origin]): Action[AnyContent] = AuthorisedAction(fetchPersonDetails = false) {
    implicit pertaxContext =>
      Future.successful {

        continueUrl.orElse(origin.map(configDecorator.getFeedbackSurveyUrl)).fold(BadRequest("Missing origin")) { url =>

          pertaxContext.user match {
            case Some(user) if user.isGovernmentGateway =>
              Redirect(configDecorator.getCompanyAuthFrontendSignOutUrl(url))
            case _ =>
              Redirect(configDecorator.citizenAuthFrontendSignOut).withSession("postLogoutPage" -> url)
          }
        }
      }
  }

  def handleSelfAssessment = ProtectedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      enforceGovernmentGatewayUser {

        selfAssessmentService.getSelfAssessmentActionNeeded(pertaxContext.authContext) flatMap {

          case ActivateSelfAssessmentActionNeeded(_) =>
            Future.successful(Redirect(configDecorator.ssoToActivateSaEnrolmentPinUrl))
          case _ =>
            cspPartialService.webchatClickToChatScriptPartial("pertax") map { p =>
              Ok(views.html.selfAssessmentNotShown(p.successfulContentOrEmpty))
            }
        }

      }
  }

  def ivExemptLandingPage(continueUrl: Option[String]): Action[AnyContent] = AuthorisedAction() {
    implicit pertaxContext =>

      val c = configDecorator.lostCredentialsChooseAccountUrl(continueUrl.getOrElse(controllers.routes.ApplicationController.index().url))

      selfAssessmentService.getSelfAssessmentActionNeeded(pertaxContext.authContext) flatMap {
        case FileReturnSelfAssessmentActionNeeded(x) =>
          handleIvExemptAuditing("Activated online SA filer")
          Future.successful(Ok(views.html.activatedSaFilerIntermediate(x.toString, DateTimeTools.previousAndCurrentTaxYear)))
        case ActivateSelfAssessmentActionNeeded(_) =>
          handleIvExemptAuditing("Not yet activated SA filer")
          Future.successful(Ok(views.html.iv.failure.failedIvContinueToActivateSa()))
        case NoEnrolmentFoundSelfAssessmentActionNeeded(_) =>
          handleIvExemptAuditing("Ambiguous SA filer")
          cspPartialService.webchatClickToChatScriptPartial("pertax") map { p =>
            Ok(views.html.iv.failure.failedIvSaFilerWithNoEnrolment(c, p.successfulContentOrEmpty))
          }

        case NoSelfAssessmentActionNeeded =>
          Future.successful(Ok(views.html.iv.failure.insufficientEvidenceNonSaFiler()))
      }
  }

  private def handleIvExemptAuditing(saUserType: String)(implicit hc: HeaderCarrier, pertaxContext: PertaxContext) = {
    auditConnector.sendEvent(buildEvent("saIdentityVerificationBypass", "sa17_exceptions_or_insufficient_evidence", Map("saUserType" -> Some(saUserType))))
  }

}
