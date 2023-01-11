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

package controllers.controllershelpers

import com.google.inject.{Inject, Singleton}
import config.{ConfigDecorator, NewsAndTilesConfig}
import controllers.auth.requests.UserRequest
import models._
import models.admin.{ChildBenefitSingleAccountToggle, NationalInsuranceTileToggle}
import play.api.i18n.Messages
import play.api.mvc.AnyContent
import play.twirl.api.{Html, HtmlFormat}
import services.admin.FeatureFlagService
import util.DateTimeTools.current
import util.EnrolmentsHelper
import viewmodels.TaxCalculationViewModel
import views.html.cards.home._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeCardGenerator @Inject() (
  featureFlagService: FeatureFlagService,
  payAsYouEarnView: PayAsYouEarnView,
  taxCalculationView: TaxCalculationView,
  selfAssessmentView: SelfAssessmentView,
  nationalInsuranceView: NationalInsuranceView,
  taxCreditsView: TaxCreditsView,
  childBenefitView: ChildBenefitView,
  childBenefitSingleAccountView: ChildBenefitSingleAccountView,
  marriageAllowanceView: MarriageAllowanceView,
  statePensionView: StatePensionView,
  taxSummariesView: TaxSummariesView,
  seissView: SeissView,
  latestNewsAndUpdatesView: LatestNewsAndUpdatesView,
  saAndItsaMergeView: SaAndItsaMergeView,
  enrolmentsHelper: EnrolmentsHelper,
  newsAndTilesConfig: NewsAndTilesConfig
)(implicit configDecorator: ConfigDecorator, ex: ExecutionContext) {

  def getIncomeCards(
    taxComponentsState: TaxComponentsState,
    taxCalculationStateCyMinusOne: Option[TaxYearReconciliation],
    taxCalculationStateCyMinusTwo: Option[TaxYearReconciliation],
    saActionNeeded: SelfAssessmentUserType,
    showSeissCard: Boolean
  )(implicit request: UserRequest[AnyContent], messages: Messages): Future[Seq[Html]] =
    Future
      .sequence(
        List(
          Future.successful(getLatestNewsAndUpdatesCard()),
          Future.successful(getPayAsYouEarnCard(taxComponentsState)),
          Future.successful(getTaxCalculationCard(taxCalculationStateCyMinusOne)),
          Future.successful(getTaxCalculationCard(taxCalculationStateCyMinusTwo)),
          Future.successful(getSaAndItsaMergeCard()),
          getNationalInsuranceCard(),
          Future.successful(if (request.trustedHelper.isEmpty) {
            getAnnualTaxSummaryCard
          } else {
            None
          })
        )
      )
      .map(_.flatten)

  def getPayAsYouEarnCard(
    taxComponentsState: TaxComponentsState
  )(implicit request: UserRequest[_], messages: Messages): Option[HtmlFormat.Appendable] =
    request.nino.flatMap { _ =>
      taxComponentsState match {
        case TaxComponentsNotAvailableState => None
        case _                              => Some(payAsYouEarnView(configDecorator))
      }
    }

  def getTaxCalculationCard(
    taxYearReconciliations: Option[TaxYearReconciliation]
  )(implicit messages: Messages): Option[HtmlFormat.Appendable] =
    taxYearReconciliations
      .flatMap(TaxCalculationViewModel.fromTaxYearReconciliation)
      .map(taxCalculationView(_))

  def getSaAndItsaMergeCard()(implicit
    messages: Messages,
    request: UserRequest[_]
  ): Option[HtmlFormat.Appendable] =
    if (
      request.trustedHelper.isEmpty &&
      (enrolmentsHelper.itsaEnrolmentStatus(request.enrolments).isDefined || request.isSa)
    ) {
      Some(
        saAndItsaMergeView(
          (current.currentYear + 1).toString,
          enrolmentsHelper.itsaEnrolmentStatus(request.enrolments).isDefined
        )
      )
    } else {
      None
    }

  def getAnnualTaxSummaryCard(implicit
    request: UserRequest[AnyContent],
    messages: Messages
  ): Option[HtmlFormat.Appendable] =
    if (configDecorator.isAtsTileEnabled) {
      val url = if (request.isSaUserLoggedIntoCorrectAccount) {
        configDecorator.annualTaxSaSummariesTileLink
      } else {
        configDecorator.annualTaxPayeSummariesTileLink
      }

      Some(taxSummariesView(url))
    } else {
      None
    }

  def getLatestNewsAndUpdatesCard()(implicit messages: Messages): Option[HtmlFormat.Appendable] =
    if (configDecorator.isNewsAndUpdatesTileEnabled && newsAndTilesConfig.getNewsAndContentModelList().nonEmpty) {
      Some(latestNewsAndUpdatesView())
    } else {
      None
    }

  def getNationalInsuranceCard()(implicit messages: Messages): Future[Option[HtmlFormat.Appendable]] =
    featureFlagService.get(NationalInsuranceTileToggle).map { toggle =>
      if (toggle.isEnabled) {
        Some(nationalInsuranceView())
      } else {
        None
      }
    }

  def getBenefitCards(
    taxComponents: Option[TaxComponents]
  )(implicit messages: Messages): Seq[Html] =
    List(
      getTaxCreditsCard(configDecorator.taxCreditsPaymentLinkEnabled),
      getChildBenefitCard(),
      getMarriageAllowanceCard(taxComponents)
    ).flatten

  def getTaxCreditsCard(showTaxCreditsPaymentLink: Boolean)(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(taxCreditsView(showTaxCreditsPaymentLink))

  def getChildBenefitCard()(implicit messages: Messages): Option[HtmlFormat.Appendable] =
    featureFlagService
      .get(ChildBenefitSingleAccountToggle)
      .map { toggle =>
        if (toggle.isEnabled) {
          Some(childBenefitSingleAccountView())
        } else {
          None
        }
      }
      .value
      .get
      .get

  def getMarriageAllowanceCard(taxComponents: Option[TaxComponents])(implicit
    messages: Messages
  ): Some[HtmlFormat.Appendable] =
    Some(marriageAllowanceView(taxComponents))

  def getPensionCards()(implicit messages: Messages): Seq[Html] =
    List(
      getStatePensionCard()
    ).flatten

  def getStatePensionCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(statePensionView())

}
