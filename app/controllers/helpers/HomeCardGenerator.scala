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

package controllers.helpers

import javax.inject.Singleton

import models._
import play.api.i18n.Messages
import play.twirl.api.Html
import models.SelfAssessmentUserType
import models.TaxSummary
import util.DateTimeTools.previousAndCurrentTaxYear

@Singleton
class HomeCardGenerator {

  def getIncomeCards(pertaxUser: Option[PertaxUser], taxSummary: Option[TaxSummary],
                     taxCalculationState: TaxCalculationState,
                     saActionNeeded: SelfAssessmentUserType)(implicit pertaxContext: PertaxContext, messages: Messages): Seq[Html] = List(
    getPayAsYouEarnCard(pertaxUser, taxSummary),
    getTaxCalculationCard(taxCalculationState),
    getSelfAssessmentCard(saActionNeeded),
    getNationalInsuranceCard()
  ).flatten

  def getBenefitCards(taxSummary: Option[TaxSummary])(implicit pertaxContext: PertaxContext, messages: Messages): Seq[Html] = List(
    getTaxCreditsCard(),
    getChildBenefitCard(),
    getMarriageAllowanceCard(taxSummary)
  ).flatten

  def getPensionCards(pertaxUser: Option[PertaxUser])(implicit pertaxContext: PertaxContext, messages: Messages): Seq[Html] = List(
    getStatePensionCard()
  ).flatten

  def getPayAsYouEarnCard(pertaxUser: Option[PertaxUser], taxSummary: Option[TaxSummary])(implicit messages: Messages) = {

    pertaxUser match {
      case Some(u) if u.isPaye => taxSummary.map(ts => views.html.cards.home.payAsYouEarn(ts.isCompanyBenefitRecipient))
      case _ => None
    }
  }

  def getTaxCalculationCard(taxCalculationState: TaxCalculationState)(implicit pertaxContext: PertaxContext, messages: Messages) = {

    taxCalculationState match {
      case _:TaxCalculationUnderpaidPaymentsDownState => None
      case TaxCalculationUnkownState => None
      case taxCalculationState => Some(views.html.cards.home.taxCalculation(taxCalculationState))
    }
  }

  def getSelfAssessmentCard(saActionNeeded: SelfAssessmentUserType)(implicit pertaxContext: PertaxContext, messages: Messages) = {
    if (!pertaxContext.user.fold(false)(_.isVerify)) {
      saActionNeeded match {
        case NonFilerSelfAssessmentUser => None
        case saActionNeeded =>
          Some(views.html.cards.home.selfAssessment(saActionNeeded, previousAndCurrentTaxYear))
      }
    } else {
      None
    }
  }

  def getNationalInsuranceCard()(implicit messages: Messages) = {
    Some(views.html.cards.home.nationalInsurance())
  }

  def getTaxCreditsCard()(implicit messages: Messages) = {
    Some(views.html.cards.home.taxCredits())
  }

  def getChildBenefitCard()(implicit messages: Messages) = {
    Some(views.html.cards.home.childBenefit())
  }

  def getMarriageAllowanceCard(taxSummary: Option[TaxSummary])(implicit messages: Messages) = {
    Some(views.html.cards.home.marriageAllowance(taxSummary))
  }

  def getStatePensionCard()(implicit messages: Messages) = {
    Some(views.html.cards.home.statePension())
  }

}
