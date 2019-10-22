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

package controllers.helpers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import com.google.inject.{Inject, Singleton}
import models._
import play.api.i18n.Messages
import play.twirl.api.{Html, HtmlFormat}
import util.DateTimeTools.previousAndCurrentTaxYear
import viewmodels.TaxCalculationViewModel

@Singleton
class HomeCardGenerator @Inject()(implicit configDecorator: ConfigDecorator) {

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
        case _                              => Some(views.html.cards.home.payAsYouEarn())
      }
    }

  def getTaxCalculationCard(taxYearReconciliations: Option[TaxYearReconciliation])(
    implicit messages: Messages): Option[HtmlFormat.Appendable] =
    taxYearReconciliations
      .flatMap(TaxCalculationViewModel.fromTaxYearReconciliation)
      .map(views.html.cards.home.taxCalculation(_))

  def getSelfAssessmentCard(saActionNeeded: SelfAssessmentUserType, nextDeadlineTaxYear: Int)(
    implicit request: UserRequest[_],
    messages: Messages): Option[HtmlFormat.Appendable] =
    if (!request.isVerify) {
      saActionNeeded match {
        case NonFilerSelfAssessmentUser => None
        case saWithActionNeeded =>
          Some(
            views.html.cards.home
              .selfAssessment(saWithActionNeeded, previousAndCurrentTaxYear, nextDeadlineTaxYear.toString))
      }
    } else {
      None
    }

  def getNationalInsuranceCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(views.html.cards.home.nationalInsurance())

  def getTaxCreditsCard(showTaxCreditsPaymentLink: Boolean)(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(views.html.cards.home.taxCredits(showTaxCreditsPaymentLink))

  def getChildBenefitCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(views.html.cards.home.childBenefit())

  def getMarriageAllowanceCard(taxComponents: Option[TaxComponents])(
    implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(views.html.cards.home.marriageAllowance(taxComponents))

  def getStatePensionCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] =
    Some(views.html.cards.home.statePension())
}
