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

import javax.inject.{Inject, Singleton}
import config.ConfigDecorator
import models.{Address, PertaxContext, PertaxUser}
import play.twirl.api.{Html, HtmlFormat}
import org.joda.time.LocalDate
import uk.gov.hmrc.play.language.LanguageUtils.Dates.formatDate

@Singleton
class PersonalDetailsCardGenerator @Inject() (
  val configDecorator: ConfigDecorator
) {

  def getPersonalDetailsCards()(implicit pertaxContext: PertaxContext, messages: play.api.i18n.Messages): Seq[Html] = List(
    getChangeNameCard(),
    getMainAddressCard(),
    getPostalAddressCard(),
    getNationalInsuranceCard()
  ).flatten

  def show2016AddressMessage(address: Option[Address])(implicit messages: play.api.i18n.Messages): (Boolean, Option[String]) = {

    def compare(a: LocalDate, y: Int, m: Int, d: Int)(f: (LocalDate, LocalDate) => Boolean) = f(a, new LocalDate(y, m, d))

    address match {
      case Some(Address(_, _, _, _, _, _, Some(startDate), _)) if compare(startDate, 2016, 4, 6)(_.equals(_)) => (true, None)
      case Some(Address(_, _, _, _, _, _, Some(startDate), _)) if compare(startDate, 2016, 4, 6)(!_.equals(_)) => (false, Some(formatDate(startDate)))
      case _ => (false, None)
    }
  }

  private def getPersonDetails()(implicit pertaxContext: PertaxContext) = {
    for {
      u <- pertaxContext.user
      pd <- u.personDetails
    } yield {
      pd
    }
  }

  def hasCorrespondenceAddress()(implicit pertaxContext: PertaxContext): Boolean = {
    getPersonDetails.flatMap(_.correspondenceAddress).isDefined
  }

  def getMainAddressCard()(implicit pertaxContext: PertaxContext, messages: play.api.i18n.Messages): Option[HtmlFormat.Appendable] = {
    getPersonDetails match {
      case Some(personDetails) => {
        val (show2016Message, startDate) = show2016AddressMessage(personDetails.address)
        Some(views.html.cards.personaldetails.mainAddress(personDetails = personDetails, taxCreditsEnabled = configDecorator.taxCreditsEnabled, hasCorrespondenceAddress = hasCorrespondenceAddress))
      }
      case _ => None
    }
  }

  def getPostalAddressCard()(implicit pertaxContext: PertaxContext, messages: play.api.i18n.Messages): Option[HtmlFormat.Appendable] = {
    getPersonDetails match {
      case Some(personDetails) => {
        hasCorrespondenceAddress match {
          case true if !personDetails.correspondenceAddress.exists(_.isWelshLanguageUnit) => {
            val canUpdatePostalAddress = personDetails.correspondenceAddress.flatMap(_.startDate).fold(true) { _ != LocalDate.now }
            Some (views.html.cards.personaldetails.postalAddress (personDetails = personDetails, canUpdatePostalAddress = canUpdatePostalAddress) )
          }
          case _ => None
        }
      }
      case _ => None
    }
  }

  def getNationalInsuranceCard()(implicit pertaxContext: PertaxContext, messages: play.api.i18n.Messages): Option[HtmlFormat.Appendable] = {
    pertaxContext.user match {
      case Some(u) if (u.isHighGovernmentGatewayOrVerify && (u.isPaye || u.isSa)) => u.nino.map(n => views.html.cards.personaldetails.nationalInsurance(n))
      case _ => None
    }
  }

  def getChangeNameCard()(implicit pertaxContext: PertaxContext, messages: play.api.i18n.Messages): Option[HtmlFormat.Appendable] = {
    PertaxUser.ifNameAvailable {
      views.html.cards.personaldetails.changeName()
    }
  }
}
