/*
 * Copyright 2021 HM Revenue & Customs
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

package viewmodels

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.CountryHelper
import javax.inject.{Inject, Singleton}
import models.{AddressJourneyTTLModel, EditCorrespondenceAddress, EditPrimaryAddress, EditSoleAddress, PersonDetails}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.domain.Nino
import views.html.personaldetails.partials.{AddressView, CorrespondenceAddressView}
import views.html.tags.formattedNino

@Singleton
class PersonalDetailsViewModel @Inject() (
  val configDecorator: ConfigDecorator,
  val countryHelper: CountryHelper,
  addressView: AddressView,
  correspondenceAddressView: CorrespondenceAddressView
) {

  val changeMainAddressUrl = if (configDecorator.taxCreditsEnabled) {
    controllers.address.routes.TaxCreditsChoiceController.onPageLoad.url
  } else { controllers.address.routes.ResidencyChoiceController.onPageLoad.url }
  val changePostalAddressUrl = controllers.address.routes.PostalInternationalAddressChoiceController.onPageLoad.url
  val viewNinoUrl = controllers.routes.InterstitialController.displayNationalInsurance.url
  val changeNameUrl = configDecorator.changeNameLinkUrl
  val paperlessSettingsUrl = controllers.routes.PaperlessPreferencesController.managePreferences.url
  val trustedHelpersUrl = configDecorator.manageTrustedHelpersUrl

  private def getName(implicit request: UserRequest[_]) =
    request.name.map(n =>
      PersonalDetailsTableRowModel("label.name", HtmlFormat.raw(n), "label.change_your_name", Some(changeNameUrl))
    )

  private def getNationalInsurance(ninoToDisplay: Option[Nino])(implicit request: UserRequest[_]) =
    ninoToDisplay.map(n =>
      PersonalDetailsTableRowModel(
        "label.national_insurance",
        formattedNino(n),
        "label.view_your_national_insurance_letter",
        Some(viewNinoUrl)
      )
    )

  private def getMainAddress(personDetails: PersonDetails, isMainAddressChangeLocked: Boolean)(implicit
    messages: play.api.i18n.Messages
  ) =
    personDetails.address.map { address =>
      if (isMainAddressChangeLocked) {
        PersonalDetailsTableRowModel(
          "label.main_address",
          addressView(address, countryHelper.excludedCountries),
          "label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow",
          None
        )
      } else {
        PersonalDetailsTableRowModel(
          "label.main_address",
          addressView(address, countryHelper.excludedCountries),
          "label.change_your_main_address",
          Some(changeMainAddressUrl)
        )
      }
    }

  private def getPostalAddress(personDetails: PersonDetails, isCorrespondenceChangeLocked: Boolean)(implicit
    messages: play.api.i18n.Messages
  ) = {
    val optionalPostalAddress = getPostalAddressIfExists(personDetails, isCorrespondenceChangeLocked)
    if (optionalPostalAddress.isEmpty && personDetails.address.isDefined) {
      Some(
        PersonalDetailsTableRowModel(
          "label.postal_address",
          correspondenceAddressView(None, countryHelper.excludedCountries),
          "label.change_your_postal_address",
          Some(changePostalAddressUrl)
        )
      )
    } else {
      optionalPostalAddress
    }
  }

  private def getPostalAddressIfExists(personDetails: PersonDetails, isCorrespondenceChangeLocked: Boolean)(implicit
    messages: play.api.i18n.Messages
  ) =
    if (!personDetails.correspondenceAddress.exists(_.isWelshLanguageUnit)) {
      personDetails.correspondenceAddress.map { correspondenceAddress =>
        if (isCorrespondenceChangeLocked) {
          PersonalDetailsTableRowModel(
            "label.postal_address",
            correspondenceAddressView(Some(correspondenceAddress), countryHelper.excludedCountries),
            "label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow",
            None
          )
        } else {
          PersonalDetailsTableRowModel(
            "label.postal_address",
            correspondenceAddressView(Some(correspondenceAddress), countryHelper.excludedCountries),
            "label.change_your_postal_address",
            Some(changePostalAddressUrl)
          )
        }
      }
    } else {
      None
    }

  def getPersonDetailsTable(changedAddressIndicator: List[AddressJourneyTTLModel], ninoToDisplay: Option[Nino])(implicit
    request: UserRequest[_],
    messages: play.api.i18n.Messages
  ): Seq[PersonalDetailsTableRowModel] = {

    val optionalEditAddress = changedAddressIndicator.map(y => y.editedAddress)
    val isMainAddressChangeLocked = optionalEditAddress.exists(_.isInstanceOf[EditSoleAddress]) || optionalEditAddress
      .exists(_.isInstanceOf[EditPrimaryAddress])
    val isCorrespondenceChangeLocked =
      optionalEditAddress.exists(_.isInstanceOf[EditCorrespondenceAddress])

    val nameRow = getName
    val ninoRow = getNationalInsurance(ninoToDisplay)
    val mainAddressRow = request.personDetails.map(getMainAddress(_, isMainAddressChangeLocked)).getOrElse(None)
    val postalAddressRow = request.personDetails.map(getPostalAddress(_, isCorrespondenceChangeLocked)).getOrElse(None)

    Seq(
      nameRow,
      ninoRow,
      mainAddressRow,
      postalAddressRow
    ).flatten[PersonalDetailsTableRowModel]

  }

  def getTrustedHelpersRow(implicit request: UserRequest[_]): Option[PersonalDetailsTableRowModel] = if (
    request.isVerify
  ) {
    Some(
      PersonalDetailsTableRowModel(
        "label.trusted_helpers",
        HtmlFormat.raw("label.manage_trusted_helpers"),
        "label.change_trusted_helpers",
        Some(trustedHelpersUrl)
      )
    )
  } else { None }

  def getPaperlessSettingsRow(implicit request: UserRequest[_]): Option[PersonalDetailsTableRowModel] = if (
    request.isGovernmentGateway
  ) {
    Some(
      PersonalDetailsTableRowModel(
        "label.go_paperless",
        HtmlFormat.raw("label.go_paperless_content"),
        "label.go_paperless_change",
        Some(paperlessSettingsUrl)
      )
    )
  } else { None }

  def getSignInDetailsRow(implicit request: UserRequest[_]): Option[PersonalDetailsTableRowModel] = if (
    request.isGovernmentGateway
  ) {
    request.profile.map(profileUrl =>
      PersonalDetailsTableRowModel(
        "label.sign_in_details",
        HtmlFormat.raw("label.sign_in_details_content"),
        "label.sign_in_details_change",
        Some(profileUrl)
      )
    )
  } else { None }
}
