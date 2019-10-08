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
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import controllers.helpers.PaperlessInterruptHelper
import error.LocalErrorHandler
import javax.inject.Inject
import models._
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, ActionBuilder, AnyContent, Request}
import play.twirl.api.Html
import services.partials.{FormPartialService, MessageFrontendService, SaPartialService}
import services.{CitizenDetailsService, PreferencesFrontendService, UserDetailsService}
import uk.gov.hmrc.play.partials.HtmlPartial
import util.DateTimeTools.previousAndCurrentTaxYearFromGivenYear
import error.RendersErrors
import util.LocalPartialRetriever

import scala.concurrent.Future

class InterstitialController @Inject()(
  val messagesApi: MessagesApi,
  val formPartialService: FormPartialService,
  val saPartialService: SaPartialService,
  val citizenDetailsService: CitizenDetailsService,
  val userDetailsService: UserDetailsService,
  val delegationConnector: FrontEndDelegationConnector,
  val preferencesFrontendService: PreferencesFrontendService,
  val messageFrontendService: MessageFrontendService,
  val localErrorHandler: LocalErrorHandler,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  auditConnector: PertaxAuditConnector,
  authConnector: PertaxAuthConnector)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator)
    extends PertaxBaseController with PaperlessInterruptHelper with RendersErrors {

  val saBreadcrumb: Breadcrumb =
    "label.self_assessment" -> routes.InterstitialController.displaySelfAssessment().url ::
      baseBreadcrumb

  private def currentUrl(implicit request: Request[AnyContent]) =
    configDecorator.pertaxFrontendHost + request.path

  private val authenticate: ActionBuilder[UserRequest] = authJourney.auth andThen withBreadcrumbAction.addBreadcrumb(
    baseBreadcrumb)
  private val authenticateSa: ActionBuilder[UserRequest] = authJourney.auth andThen withBreadcrumbAction.addBreadcrumb(
    saBreadcrumb)

  def displayNationalInsurance: Action[AnyContent] = authenticate.async { implicit request =>
    formPartialService.getNationalInsurancePartial.map { p =>
      Ok(
        views.html.interstitial.viewNationalInsuranceInterstitialHome(
          formPartial = p successfulContentOrElse Html(""),
          redirectUrl = currentUrl))
    }
  }

  def displayChildBenefits: Action[AnyContent] = authenticate { implicit request =>
    Ok(
      views.html.interstitial.viewChildBenefitsSummaryInterstitial(
        redirectUrl = currentUrl,
        taxCreditsEnabled = configDecorator.taxCreditsEnabled))
  }

  def displaySelfAssessment: Action[AnyContent] = authenticate.async { implicit request =>
    val formPartial = formPartialService.getSelfAssessmentPartial recoverWith {
      case _ => Future.successful(HtmlPartial.Failure(None, ""))
    }
    val saPartial = saPartialService.getSaAccountSummary recoverWith {
      case _ => Future.successful(HtmlPartial.Failure(None, ""))
    }

    if (request.isSa && request.isGovernmentGateway) {
      for {
        formPartial <- formPartial
        saPartial   <- saPartial
      } yield {
        Ok(
          views.html.selfAssessmentSummary(
            formPartial successfulContentOrElse Html(""),
            saPartial successfulContentOrElse Html("")
          ))
      }
    } else futureError(UNAUTHORIZED)

  }

  def displaySa302Interrupt(year: Int): Action[AnyContent] = authenticateSa { implicit request =>
    if (request.isSa) {
      request.saUserType match {
        case saUser: SelfAssessmentUser => {
          Ok(
            views.html.selfassessment
              .sa302Interrupt(year = previousAndCurrentTaxYearFromGivenYear(year), saUtr = saUser.saUtr))
        }
        case NonFilerSelfAssessmentUser => {
          Logger.warn("User had no sa account (non filer) when one was required")
          error(INTERNAL_SERVER_ERROR)
        }
      }
    } else {
      Logger.warn("User had no sa account when one was required")
      error(UNAUTHORIZED)
    }
  }
}
