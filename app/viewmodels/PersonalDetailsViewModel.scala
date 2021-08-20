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

  private val changeMainAddressUrl = if (configDecorator.taxCreditsEnabled) {
    controllers.address.routes.TaxCreditsChoiceController.onPageLoad.url
  } else { controllers.address.routes.ResidencyChoiceController.onPageLoad.url }
  private val changePostalAddressUrl =
    controllers.address.routes.PostalInternationalAddressChoiceController.onPageLoad.url
  private val viewNinoUrl = controllers.routes.InterstitialController.displayNationalInsurance.url
  private val changeNameUrl = configDecorator.changeNameLinkUrl
  private val paperlessSettingsUrl = controllers.routes.PaperlessPreferencesController.managePreferences.url
  private val trustedHelpersUrl = configDecorator.manageTrustedHelpersUrl

  private def getName(implicit request: UserRequest[_]) =
    request.name.map(n =>
      PersonalDetailsTableRowModel(
        "name",
        "label.name",
        HtmlFormat.raw(n),
        "label.change",
        "label.your_name",
        Some(changeNameUrl)
      )
    )

  private def getNationalInsurance(ninoToDisplay: Option[Nino])(implicit request: UserRequest[_]) =
    ninoToDisplay.map(n =>
      PersonalDetailsTableRowModel(
        "national_insurance",
        "label.national_insurance",
        formattedNino(n),
        "label.view_your_national_insurance_letter",
        "nino",
        Some(viewNinoUrl)
      )
    )

  private def getMainAddress(personDetails: PersonDetails, isMainAddressChangeLocked: Boolean)(implicit
    messages: play.api.i18n.Messages
  ) =
    personDetails.address.map { address =>
      if (isMainAddressChangeLocked) {
        PersonalDetailsTableRowModel(
          "main_address",
          "label.main_address",
          addressView(address, countryHelper.excludedCountries),
          "label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow",
          "address",
          None
        )
      } else {
        PersonalDetailsTableRowModel(
          "main_address",
          "label.main_address",
          addressView(address, countryHelper.excludedCountries),
          "label.change_your_main_address",
          "address",
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
          "postal_address",
          "label.postal_address",
          correspondenceAddressView(None, countryHelper.excludedCountries),
          "label.change_your_postal_address",
          "address",
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
            "postal_address",
            "label.postal_address",
            correspondenceAddressView(Some(correspondenceAddress), countryHelper.excludedCountries),
            "label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow",
            "address",
            None
          )
        } else {
          PersonalDetailsTableRowModel(
            "postal_address",
            "label.postal_address",
            correspondenceAddressView(Some(correspondenceAddress), countryHelper.excludedCountries),
            "label.change_your_postal_address",
            "address",
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

  def getTrustedHelpersRow(implicit
    request: UserRequest[_],
    messages: play.api.i18n.Messages
  ): Option[PersonalDetailsTableRowModel] = if (request.isVerify) {
    Some(
      PersonalDetailsTableRowModel(
        "trusted_helpers",
        "label.trusted_helpers",
        HtmlFormat.raw(messages("label.manage_trusted_helpers")),
        "label.change_trusted_helpers",
        "address",
        Some(trustedHelpersUrl)
      )
    )
  } else { None }

  def getPaperlessSettingsRow(implicit
    request: UserRequest[_],
    messages: play.api.i18n.Messages
  ): Option[PersonalDetailsTableRowModel] = if (request.isGovernmentGateway) {
    Some(
      PersonalDetailsTableRowModel(
        "paperless",
        "label.go_paperless",
        HtmlFormat.raw(messages("label.go_paperless_content")),
        "label.go_paperless_change",
        "address",
        Some(paperlessSettingsUrl)
      )
    )
  } else { None }

  def getSignInDetailsRow(implicit
    request: UserRequest[_],
    messages: play.api.i18n.Messages
  ): Option[PersonalDetailsTableRowModel] = if (request.isGovernmentGateway) {
    request.profile.map(profileUrl =>
      PersonalDetailsTableRowModel(
        "sign_in_details",
        "label.sign_in_details",
        HtmlFormat.raw(messages("label.sign_in_details_content")),
        "label.sign_in_details_change",
        "address",
        Some(profileUrl)
      )
    )
  } else { None }
}
