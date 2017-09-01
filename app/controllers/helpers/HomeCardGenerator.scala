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

package controllers.helpers

import javax.inject.Singleton

import models._
import play.api.i18n.{Messages, MessagesApi}
import play.twirl.api.Html
import models.SelfAssessmentUserType
import models.TaxSummary
import util.DateTimeTools.previousAndCurrentTaxYear

@Singleton
class HomeCardGenerator {

  def getIncomeCards(pertaxUser: Option[PertaxUser], taxSummary: Option[TaxSummary],
                     taxCalculationState: TaxCalculationState,
                     saActionNeeded: SelfAssessmentUserType)(implicit pertaxContext: PertaxContext, messages: Messages, messagesApi: MessagesApi): Seq[Html] = List(
    getPayAsYouEarnCard(pertaxUser, taxSummary),
    getTaxCalculationCard(taxCalculationState),
    getSelfAssessmentCard(saActionNeeded),
    getNationalInsuranceCard()
  ).flatten

  def getBenefitCards(taxSummary: Option[TaxSummary])(implicit pertaxContext: PertaxContext, messages: Messages, messagesApi: MessagesApi): Seq[Html] = List(
    getTaxCreditsCard(),
    getChildBenefitCard(),
    getMarriageAllowanceCard(taxSummary)
  ).flatten

  def getPensionCards(pertaxUser: Option[PertaxUser], hasLtaProtections: Boolean)(implicit pertaxContext: PertaxContext, messages: Messages, messagesApi: MessagesApi): Seq[Html] = List(
    getStatePensionCard(),
    getLifetimeAllowanceProtectionCard(hasLtaProtections)
  ).flatten


  def getPayAsYouEarnCard(pertaxUser: Option[PertaxUser], taxSummary: Option[TaxSummary])(implicit messages: Messages, messagesApi: MessagesApi) = {

    pertaxUser match {
      case Some(u) if u.isPaye => taxSummary.map(ts => views.html.cards.payAsYouEarn(ts.isCompanyBenefitRecipient))
      case _ => None
    }
  }

  def getTaxCalculationCard(taxCalculationState: TaxCalculationState)(implicit pertaxContext: PertaxContext, messages: Messages, messagesApi: MessagesApi) = {

    taxCalculationState match {
      case _:TaxCalculationUnderpaidPaymentsDownState => None
      case TaxCalculationUnkownState => None
      case taxCalculationState => Some(views.html.cards.taxCalculation(taxCalculationState))
    }
  }

  def getSelfAssessmentCard(saActionNeeded: SelfAssessmentUserType)(implicit pertaxContext: PertaxContext, messages: Messages, messagesApi: MessagesApi) = {

    saActionNeeded match {
      case NonFilerSelfAssessmentUser => None
      case saActionNeeded =>
        Some(views.html.cards.selfAssessment(saActionNeeded, previousAndCurrentTaxYear))
    }
  }

  def getNationalInsuranceCard()(implicit messages: Messages, messagesApi: MessagesApi) = {
    Some(views.html.cards.nationalInsurance())
  }


  def getTaxCreditsCard()(implicit messages: Messages, messagesApi: MessagesApi) = {
    Some(views.html.cards.taxCredits())
  }

  def getChildBenefitCard()(implicit messages: Messages, messagesApi: MessagesApi) = {
    Some(views.html.cards.childBenefit())
  }

  def getMarriageAllowanceCard(taxSummary: Option[TaxSummary])(implicit messages: Messages, messagesApi: MessagesApi) = {

    if (taxSummary.map(ts => ts.isMarriageAllowanceRecipient).getOrElse(false)) None
    else Some(views.html.cards.marriageAllowance())
  }


  def getStatePensionCard()(implicit messages: Messages, messagesApi: MessagesApi) = {
    Some(views.html.cards.statePension())
  }

  def getLifetimeAllowanceProtectionCard(hasLtaProtections: Boolean)(implicit messages: Messages, messagesApi: MessagesApi) = {
    if(hasLtaProtections)
      Some(views.html.cards.lifetimeAllowanceProtection())
    else None
  }

}
