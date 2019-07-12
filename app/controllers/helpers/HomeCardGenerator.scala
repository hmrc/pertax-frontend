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
import javax.inject.{Inject, Singleton}
import models._
import play.api.i18n.Messages
import play.twirl.api.{Html, HtmlFormat}
import models.SelfAssessmentUserType
import models.TaxComponents
import util.DateTimeTools.previousAndCurrentTaxYear

@Singleton
class HomeCardGenerator @Inject() (val configDecorator: ConfigDecorator) {

  def getIncomeCards(pertaxUser: Option[PertaxUser],
                     taxComponentsState: TaxComponentsState,
                     taxCalculationStateCyMinusOne: Option[TaxCalculationState],
                     taxCalculationStateCyMinusTwo: Option[TaxCalculationState],
                     saActionNeeded: SelfAssessmentUserType,
                     currentTaxYear: Int)(implicit pertaxContext: PertaxContext, messages: Messages): Seq[Html] = List(
    getPayAsYouEarnCard(pertaxUser, taxComponentsState),
    getTaxCalculationCard(taxCalculationStateCyMinusOne, currentTaxYear-1, currentTaxYear),
    getTaxCalculationCard(taxCalculationStateCyMinusTwo, currentTaxYear-2, currentTaxYear-1),
    getSelfAssessmentCard(saActionNeeded, currentTaxYear+1),
    getNationalInsuranceCard()
  ).flatten

  def getBenefitCards(taxComponents: Option[TaxComponents])(implicit pertaxContext: PertaxContext, messages: Messages): Seq[Html] = List(
    getTaxCreditsCard(configDecorator.taxCreditsPaymentLinkEnabled),
    getChildBenefitCard(),
    getMarriageAllowanceCard(taxComponents)
  ).flatten

  def getPensionCards(pertaxUser: Option[PertaxUser])(implicit pertaxContext: PertaxContext, messages: Messages): Seq[Html] = List(
    getStatePensionCard()
  ).flatten

  def getPayAsYouEarnCard(pertaxUser: Option[PertaxUser], taxComponentsState: TaxComponentsState)(implicit messages: Messages): Option[HtmlFormat.Appendable] = {
    pertaxUser match {
      case Some(u) if u.isPaye =>
        taxComponentsState match {
          case TaxComponentsNotAvailableState => None
          case _ => Some(views.html.cards.home.payAsYouEarn())
        }
      case _ => None
    }
  }

  def getTaxCalculationCard(taxCalculationState: Option[TaxCalculationState],
                            previousTaxYear: Int,
                            currentTaxYear: Int)(implicit pertaxContext: PertaxContext, messages: Messages): Option[HtmlFormat.Appendable] = {

    taxCalculationState match {
      case Some(TaxCalculationUnderpaidPaymentsDownState(_,_)) => None
      case Some(TaxCalculationUnkownState) => None
      case Some(taxCalculationState) => Some(views.html.cards.home.taxCalculation(taxCalculationState, previousTaxYear, currentTaxYear))
      case _ => None
    }
  }

  def getSelfAssessmentCard(saActionNeeded: SelfAssessmentUserType,
                            nextDeadlineTaxYear: Int)(implicit pertaxContext: PertaxContext, messages: Messages): Option[HtmlFormat.Appendable] = {
    if (!pertaxContext.user.fold(false)(_.isVerify)) {
      saActionNeeded match {
        case NonFilerSelfAssessmentUser => None
        case saActionNeeded =>
          Some(views.html.cards.home.selfAssessment(saActionNeeded, previousAndCurrentTaxYear, nextDeadlineTaxYear.toString))
      }
    } else {
      None
    }
  }

  def getNationalInsuranceCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] = {
    Some(views.html.cards.home.nationalInsurance())
  }

  def getTaxCreditsCard(showTaxCreditsPaymentLink: Boolean)(implicit messages: Messages): Some[HtmlFormat.Appendable] = {
    Some(views.html.cards.home.taxCredits(showTaxCreditsPaymentLink))
  }

  def getChildBenefitCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] = {
    Some(views.html.cards.home.childBenefit())
  }

  def getMarriageAllowanceCard(taxComponents: Option[TaxComponents])(implicit messages: Messages): Some[HtmlFormat.Appendable] = {
    Some(views.html.cards.home.marriageAllowance(taxComponents))
  }

  def getStatePensionCard()(implicit messages: Messages): Some[HtmlFormat.Appendable] = {
    Some(views.html.cards.home.statePension())
  }
}
