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

package controllers.controllershelpers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import com.google.inject.{Inject, Singleton}
import models._
import play.api.i18n.Messages
import play.twirl.api.{Html, HtmlFormat}
import util.DateTimeTools.previousAndCurrentTaxYear
import viewmodels.TaxCalculationViewModel
import views.html.cards.home._

@Singleton
class HomeCardGenerator @Inject()(
  payAsYouEarn: payAsYouEarn,
  taxCalculation: taxCalculation,
  selfAssessment: selfAssessment,
  nationalInsurance: nationalInsurance,
  taxCredits: taxCredits,
  childBenefit: childBenefit,
  marriageAllowance: marriageAllowance,
  statePension: statePension
)(implicit configDecorator: ConfigDecorator) {

  def getIncomeCards(
    taxComponentsState: TaxComponentsState,
    taxCalculationStateCyMinusOne: Option[TaxYearReconciliation],
    taxCalculationStateCyMinusTwo: Option[TaxYearReconciliation],
    saActionNeeded: SelfAssessmentUserType,
    currentTaxYear: Int)(implicit request: UserRequest[_], messages: Messages): Seq[Html] =
    List(
      getPayAsYouEarnCard(taxComponentsState),
      getTaxCalculationCard(taxCalculationStateCyMinusOne),
      getTaxCalculationCard(taxCalculationStateCyMinusTwo),
      getSelfAssessmentCard(saActionNeeded, currentTaxYear + 1),
      getNationalInsuranceCard()
    ).flatten

  def getBenefitCards(taxComponents: Option[TaxComponents])(implicit messages: Messages): Seq[Html] =
    List(
      getTaxCreditsCard(configDecorator.taxCreditsPaymentLinkEnabled),
      getChildBenefitCard(),
      getMarriageAllowanceCard(taxComponents)
    ).flatten

  def getPensionCards()(implicit messages: Messages): Seq[Html] =
    List(
      getStatePensionCard()
    ).flatten

  def getPayAsYouEarnCard(taxComponentsState: TaxComponentsState)(
    implicit request: UserRequest[_],
    messages: Messages): Option[HtmlFormat.Appendable] =
    request.nino.flatMap { _ =>
      taxComponentsState match {
        case TaxComponentsNotAvailableState => None
        case _                              => Some(payAsYouEarn(configDecorator))
      }
    }

  def getTaxCalculationCard(taxYearReconciliations: Option[TaxYearReconciliation])(
    implicit messages: Messages): Option[HtmlFormat.Appendable] =
    taxYearReconciliations
      .flatMap(TaxCalculationViewModel.fromTaxYearReconciliation)
      .map(taxCalculation(_))

  def getSelfAssessmentCard(saActionNeeded: SelfAssessmentUserType, nextDeadlineTaxYear: Int)(
    implicit request: UserRequest[_],
    messages: Messages): Option[HtmlFormat.Appendable] =
    if (!request.isVerify) {
      saActionNeeded match {
        case NonFilerSelfAssessmentUser => None
        case saWithActionNeeded =>
          Some(selfAssessment(saWithActionNeeded, previousAndCurrentTaxYear, nextDeadlineTaxYear.toString))
      }
    } else {
      None
    }

  def getNationalInsuranceCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(nationalInsurance())

  def getTaxCreditsCard(showTaxCreditsPaymentLink: Boolean)(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(taxCredits(showTaxCreditsPaymentLink))

  def getChildBenefitCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(childBenefit())

  def getMarriageAllowanceCard(taxComponents: Option[TaxComponents])(
    implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(marriageAllowance(taxComponents))

  def getStatePensionCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(statePension())
}
