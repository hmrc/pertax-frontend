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

import connectors.FrontEndDelegationConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, PertaxRegime, WithActiveTabAction}
import controllers.helpers.{HomeCardGenerator, HomePageCachingHelper, PaperlessInterruptHelper}
import javax.inject.Inject
import models._
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}
import play.twirl.api.Html
import services.partials.{CspPartialService, MessageFrontendService}
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.renderer.{ActiveTabHome, ActiveTabYourAccount}
import uk.gov.hmrc.time.CurrentTaxYear

import scala.concurrent.Future

class HomeController @Inject()(
  val messagesApi: MessagesApi,
  val citizenDetailsService: CitizenDetailsService,
  val preferencesFrontendService: PreferencesFrontendService,
  val taiService: TaiService,
  val identityVerificationFrontendService: IdentityVerificationFrontendService,
  val taxCalculationService: TaxCalculationService,
  val selfAssessmentService: SelfAssessmentService,
  val cspPartialService: CspPartialService,
  val userDetailsService: UserDetailsService,
  val messageFrontendService: MessageFrontendService,
  val delegationConnector: FrontEndDelegationConnector,
  val pertaxDependencies: PertaxDependencies,
  val pertaxRegime: PertaxRegime,
  val homeCardGenerator: HomeCardGenerator,
  val homePageCachingHelper: HomePageCachingHelper,
  val taxCalculationStateFactory: TaxCalculationStateFactory,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction
) extends PertaxBaseController with PaperlessInterruptHelper with CurrentTaxYear {

  private val authenticate: ActionBuilder[UserRequest] = authJourney.auth andThen withActiveTabAction.addActiveTab(
    ActiveTabHome)

  def index: Action[AnyContent] = authenticate.async { implicit request =>
    val showUserResearchBanner: Future[Boolean] =
      configDecorator.urLinkUrl.fold(Future.successful(false))(_ =>
        homePageCachingHelper.hasUserDismissedUrInvitation.map(!_))

    val responses: Future[(TaxComponentsState, Option[TaxYearReconciliation], Option[TaxYearReconciliation])] =
      serviceCallResponses(request.nino, current.currentYear)

    showUserResearchBanner flatMap { showUserResearchBanner =>
      enforcePaperlessPreference {
        for {
          (taxSummaryState, taxCalculationStateCyMinusOne, taxCalculationStateCyMinusTwo) <- responses
        } yield {
          val incomeCards: Seq[Html] = homeCardGenerator.getIncomeCards(
            taxSummaryState,
            taxCalculationStateCyMinusOne,
            taxCalculationStateCyMinusTwo,
            request.saUserType,
            current.currentYear)

          val benefitCards: Seq[Html] = homeCardGenerator.getBenefitCards(taxSummaryState.getTaxComponents)

          val pensionCards: Seq[Html] = homeCardGenerator.getPensionCards

          Ok(views.html.home(incomeCards, benefitCards, pensionCards, showUserResearchBanner))
        }
      }
    }
  }

  private[controllers] def serviceCallResponses(ninoOpt: Option[Nino], year: Int)(implicit hc: HeaderCarrier)
    : Future[(TaxComponentsState, Option[TaxYearReconciliation], Option[TaxYearReconciliation])] =
    ninoOpt.fold[Future[(TaxComponentsState, Option[TaxYearReconciliation], Option[TaxYearReconciliation])]](
      Future.successful((TaxComponentsDisabledState, None, None))) { nino =>
      val taxYr = if (configDecorator.taxcalcEnabled) {
        taxCalculationService.getTaxYearReconciliations(nino)
      } else {
        Future.successful(Nil)
      }

      val taxCalculationStateCyMinusOne = taxYr.map(_.find(_.taxYear == year - 1))
      val taxCalculationStateCyMinusTwo = taxYr.map(_.find(_.taxYear == year - 2))

      val taxSummaryState: Future[TaxComponentsState] = if (configDecorator.taxComponentsEnabled) {
        taiService.taxComponents(nino, year) map {
          case TaxComponentsSuccessResponse(ts) =>
            TaxComponentsAvailableState(ts)
          case TaxComponentsUnavailableResponse =>
            TaxComponentsNotAvailableState
          case _ =>
            TaxComponentsUnreachableState
        }
      } else {
        Future.successful(TaxComponentsDisabledState)
      }

      for {
        taxCalculationStateCyMinusOne <- taxCalculationStateCyMinusOne
        taxCalculationStateCyMinusTwo <- taxCalculationStateCyMinusTwo
        taxSummaryState               <- taxSummaryState
      } yield {

        (taxSummaryState, taxCalculationStateCyMinusOne, taxCalculationStateCyMinusTwo)
      }
    }

}
