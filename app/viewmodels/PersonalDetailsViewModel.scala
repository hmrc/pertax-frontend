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
import models.{AddressJourneyTTLModel, EditCorrespondenceAddress, EditPrimaryAddress, EditSoleAddress, EditedAddress, PersonDetails}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.domain.Nino
import util.TemplateFunctions
import views.html.personaldetails.partials.{AddressView, CorrespondenceAddressView}
import views.html.tags.formattedNino

@Singleton
class PersonalDetailsViewModel @Inject() (
  val configDecorator: ConfigDecorator,
  val countryHelper: CountryHelper,
  addressView: AddressView,
  correspondenceAddressView: CorrespondenceAddressView
) {

  private val changePostalAddressUrl =
    controllers.address.routes.PostalInternationalAddressChoiceController.onPageLoad.url

  private def getName(implicit request: UserRequest[_]) =
    request.name.map(name =>
      PersonalDetailsTableRowModel(
        "name",
        "label.name",
        HtmlFormat.raw(TemplateFunctions.upperCaseToTitleCase(name)),
        "label.change",
        "label.your_name",
        Some(configDecorator.changeNameLinkUrl)
      )
    )

  private def getNationalInsurance(
    ninoToDisplay: Option[Nino]
  )(implicit request: UserRequest[_]) =
    ninoToDisplay.map(n =>
      PersonalDetailsTableRowModel(
        "national_insurance",
        "label.national_insurance",
        formattedNino(n),
        "label.view_national_insurance_letter",
        "",
        Some(controllers.routes.NiLetterController.printNationalInsuranceNumber.url)
      )
    )

  private def getMainAddress(
    personDetails: PersonDetails,
    optionalEditAddress: List[EditedAddress]
  )(implicit
    messages: play.api.i18n.Messages
  ) = {
    val isMainAddressChangeLocked = optionalEditAddress.exists(
      _.isInstanceOf[EditSoleAddress]
    ) || optionalEditAddress
      .exists(_.isInstanceOf[EditPrimaryAddress])
    personDetails.address.map { address =>
      def createAddressRow(linkTextMessage: String, linkUrl: Option[String]) =
        PersonalDetailsTableRowModel(
          "main_address",
          "label.main_address",
          addressView(address, countryHelper.excludedCountries),
          linkTextMessage,
          "label.your_main_home",
          linkUrl
        )

      if (isMainAddressChangeLocked)
        createAddressRow("label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow", None)
      else {
        val changeMainAddressUrl =
          if (configDecorator.taxCreditsEnabled)
            controllers.address.routes.TaxCreditsChoiceController.onPageLoad.url
          else controllers.address.routes.ResidencyChoiceController.onPageLoad.url

        createAddressRow("label.change", Some(changeMainAddressUrl))
      }
    }
  }

  private def getPostalAddress(
    personDetails: PersonDetails,
    optionalEditAddress: List[EditedAddress]
  )(implicit
    messages: play.api.i18n.Messages
  ) = {
    val isCorrespondenceChangeLocked =
      optionalEditAddress.exists(_.isInstanceOf[EditCorrespondenceAddress])
    val postalAddress =
      getPostalAddressIfExists(personDetails, isCorrespondenceChangeLocked)

    postalAddress match {
      case Some(address) => Some(address)
      case _ if personDetails.address.isDefined =>
        Some(
          PersonalDetailsTableRowModel(
            "postal_address",
            "label.postal_address",
            correspondenceAddressView(None, countryHelper.excludedCountries),
            "label.change",
            "label.your.postal_address",
            Some(changePostalAddressUrl)
          )
        )
      case _ => None
    }
  }

  private def getPostalAddressIfExists(
    personDetails: PersonDetails,
    isCorrespondenceChangeLocked: Boolean
  )(implicit
    messages: play.api.i18n.Messages
  ) =
    personDetails.correspondenceAddress.find(!_.isWelshLanguageUnit).map { correspondenceAddress =>
      def createRow(linkTextMessage: String, linkUrl: Option[String]) =
        PersonalDetailsTableRowModel(
          "postal_address",
          "label.postal_address",
          correspondenceAddressView(
            Some(correspondenceAddress),
            countryHelper.excludedCountries
          ),
          linkTextMessage,
          "label.your.postal_address",
          linkUrl
        )
      if (isCorrespondenceChangeLocked)
        createRow("label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow", None)
      else
        createRow("label.change", Some(changePostalAddressUrl))
    }

  def getPersonDetailsTable(
    changedAddressIndicator: List[AddressJourneyTTLModel],
    ninoToDisplay: Option[Nino]
  )(implicit
    request: UserRequest[_],
    messages: play.api.i18n.Messages
  ): Seq[PersonalDetailsTableRowModel] = {
    val optionalEditAddress = changedAddressIndicator.map(y => y.editedAddress)
    val nameRow: Option[PersonalDetailsTableRowModel] = getName
    val ninoRow: Option[PersonalDetailsTableRowModel] = getNationalInsurance(ninoToDisplay)
    val mainAddressRow: Option[PersonalDetailsTableRowModel] = request.personDetails
      .flatMap(getMainAddress(_, optionalEditAddress))
    val postalAddressRow: Option[PersonalDetailsTableRowModel] = request.personDetails
      .flatMap(getPostalAddress(_, optionalEditAddress))

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
  ): Option[PersonalDetailsTableRowModel] =
    if (request.isVerify)
      Some(
        PersonalDetailsTableRowModel(
          "trusted_helpers",
          "label.trusted_helpers",
          HtmlFormat.raw(messages("label.manage_trusted_helpers")),
          "label.change",
          "label.your_trusted_helpers",
          Some(configDecorator.manageTrustedHelpersUrl)
        )
      )
    else None

  def getPaperlessSettingsRow(implicit
    request: UserRequest[_],
    messages: play.api.i18n.Messages
  ): Option[PersonalDetailsTableRowModel] =
    if (request.isGovernmentGateway)
      Some(
        PersonalDetailsTableRowModel(
          "paperless",
          "label.go_paperless",
          HtmlFormat.raw(messages("label.go_paperless_content")),
          "label.change",
          "label.your_paperless_settings",
          Some(controllers.routes.PaperlessPreferencesController.managePreferences.url)
        )
      )
    else None

  def getSignInDetailsRow(implicit
    request: UserRequest[_],
    messages: play.api.i18n.Messages
  ): Option[PersonalDetailsTableRowModel] =
    if (request.isGovernmentGateway)
      request.profile.map(profileUrl =>
        PersonalDetailsTableRowModel(
          "sign_in_details",
          "label.sign_in_details",
          HtmlFormat.raw(messages("label.sign_in_details_content")),
          "label.change",
          "label.your_gg_details",
          Some(profileUrl)
        )
      )
    else None
}
