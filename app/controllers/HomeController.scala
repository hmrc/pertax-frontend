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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.controllershelpers.{HomeCardGenerator, HomePageCachingHelper, PaperlessInterruptHelper}
import com.google.inject.Inject
import models._
import org.joda.time.DateTime
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.renderer.{ActiveTabHome, TemplateRenderer}
import uk.gov.hmrc.time.CurrentTaxYear
import util.LocalPartialRetriever
import viewmodels.HomeViewModel

import scala.concurrent.{ExecutionContext, Future}

class HomeController @Inject()(
  val preferencesFrontendService: PreferencesFrontendService,
  val taiService: TaiService,
  val taxCalculationService: TaxCalculationService,
  val homeCardGenerator: HomeCardGenerator,
  val homePageCachingHelper: HomePageCachingHelper,
  authJourney: AuthJourney,
  withActiveTabAction: WithActiveTabAction,
  cc: MessagesControllerComponents)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends PertaxBaseController(cc) with PaperlessInterruptHelper with CurrentTaxYear {

  override def now: () => DateTime = () => DateTime.now()

  private val authenticate
    : ActionBuilder[UserRequest, AnyContent] = authJourney.authWithPersonalDetails andThen withActiveTabAction
    .addActiveTab(ActiveTabHome)

  def index: Action[AnyContent] = authenticate.async { implicit request =>
    val showUserResearchBanner: Future[Boolean] =
      configDecorator.bannerLinkUrl.fold(Future.successful(false))(_ =>
        homePageCachingHelper.hasUserDismissedUrInvitation.map(!_))

    val responses: Future[(TaxComponentsState, Option[TaxYearReconciliation], Option[TaxYearReconciliation])] =
      serviceCallResponses(request.nino, current.currentYear)

    showUserResearchBanner flatMap { showUserResearchBanner =>
      enforcePaperlessPreference {
        for {
          (taxSummaryState, taxCalculationStateCyMinusOne, taxCalculationStateCyMinusTwo) <- responses
        } yield {
          val saUserType = request.saUserType

          val incomeCards: Seq[Html] = homeCardGenerator.getIncomeCards(
            taxSummaryState,
            taxCalculationStateCyMinusOne,
            taxCalculationStateCyMinusTwo,
            saUserType,
            current.currentYear)

          val benefitCards: Seq[Html] = homeCardGenerator.getBenefitCards(taxSummaryState.getTaxComponents)

          val pensionCards: Seq[Html] = homeCardGenerator.getPensionCards

          Ok(
            views.html.home(HomeViewModel(incomeCards, benefitCards, pensionCards, showUserResearchBanner, saUserType)))
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
