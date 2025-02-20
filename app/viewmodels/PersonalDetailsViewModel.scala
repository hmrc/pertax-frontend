/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.CountryHelper
import models._
import models.admin.AddressChangeAllowedToggle
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import util.TemplateFunctions
import views.html.personaldetails.partials.{AddressUnavailableView, AddressView, CorrespondenceAddressView}
import views.html.tags.formattedNino

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PersonalDetailsViewModel @Inject() (
  configDecorator: ConfigDecorator,
  countryHelper: CountryHelper,
  addressView: AddressView,
  correspondenceAddressView: CorrespondenceAddressView,
  preferencesFrontendConnector: PreferencesFrontendConnector,
  featureFlagService: FeatureFlagService,
  addressUnavailableView: AddressUnavailableView
)(implicit ec: ExecutionContext) {

  private def getMainAddress(
    personDetails: Option[PersonDetails],
    optionalEditAddress: List[EditedAddress]
  )(implicit messages: play.api.i18n.Messages): Future[Option[PersonalDetailsTableRowModel]] = {
    val isMainAddressChangeLocked = optionalEditAddress.exists(
      _.isInstanceOf[EditResidentialAddress]
    )

    for {
      addressChangeAllowedToggle <- featureFlagService.get(AddressChangeAllowedToggle)
    } yield
      if (addressChangeAllowedToggle.isEnabled) {
        if (isMainAddressChangeLocked) {
          personDetails.flatMap(_.address.map { address =>
            PersonalDetailsTableRowModel(
              "main_address",
              "label.main_address",
              addressView(address, countryHelper.excludedCountries),
              "label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow",
              "label.your_main_home",
              None
            )
          })
        } else {
          personDetails.flatMap(_.address.map { address =>
            PersonalDetailsTableRowModel(
              "main_address",
              "label.main_address",
              addressView(address, countryHelper.excludedCountries),
              "label.change",
              "label.your_main_home",
              Some(AddressRowModel.changeMainAddressUrl)
            )
          })
        }
      } else {
        Option(
          PersonalDetailsTableRowModel(
            id = "main_address",
            titleMessage = "label.main_address",
            content = addressUnavailableView(displayAllLettersLine = false),
            linkTextMessage = "",
            visuallyhiddenText = "label.your_main_home",
            linkUrl = None
          )
        )
      }
  }

  private def getPostalAddress(
    personDetails: Option[PersonDetails],
    optionalEditAddress: List[EditedAddress]
  )(implicit
    messages: play.api.i18n.Messages
  ): Future[Option[PersonalDetailsTableRowModel]] = {
    val isCorrespondenceChangeLocked =
      optionalEditAddress.exists(_.isInstanceOf[EditCorrespondenceAddress])
    val postalAddress                =
      getPostalAddressIfExists(personDetails, isCorrespondenceChangeLocked)

    for {
      addressChangeAllowedToggle <- featureFlagService.get(AddressChangeAllowedToggle)
    } yield
      if (addressChangeAllowedToggle.isEnabled) {
        postalAddress match {
          case Some(address) => Some(address)
          case _             =>
            personDetails.flatMap(_.address.map { address =>
              PersonalDetailsTableRowModel(
                "postal_address",
                "label.postal_address",
                correspondenceAddressView(Some(address), countryHelper.excludedCountries),
                "label.change",
                "label.your.postal_address",
                Some(AddressRowModel.changePostalAddressUrl),
                isPostalAddressSame = true
              )
            })
        }
      } else {
        Some(
          PersonalDetailsTableRowModel(
            id = "postal_address",
            titleMessage = "label.postal_address",
            content = addressUnavailableView(displayAllLettersLine = true),
            linkTextMessage = "",
            visuallyhiddenText = "label.your.postal_address",
            linkUrl = None
          )
        )
      }
  }

  private def getPostalAddressIfExists(
    personDetails: Option[PersonDetails],
    isCorrespondenceChangeLocked: Boolean
  )(implicit
    messages: play.api.i18n.Messages
  ) =
    personDetails.flatMap(_.correspondenceAddress.find(!_.isWelshLanguageUnit).map { correspondenceAddress =>
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

      if (isCorrespondenceChangeLocked) {
        createRow("label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow", None)
      } else {
        createRow("label.change", Some(AddressRowModel.changePostalAddressUrl))
      }
    })

  def getAddressRow(personDetails: Option[PersonDetails], addressModel: List[AddressJourneyTTLModel])(implicit
    messages: play.api.i18n.Messages
  ): Future[AddressRowModel] = {
    val optionalEditAddress                                            = addressModel.map(y => y.editedAddress)
    val mainAddressRow: Future[Option[PersonalDetailsTableRowModel]]   =
      getMainAddress(personDetails, optionalEditAddress)
    val postalAddressRow: Future[Option[PersonalDetailsTableRowModel]] =
      getPostalAddress(personDetails, optionalEditAddress)
    for {
      mainAddressVal   <- mainAddressRow
      postalAddressVal <- postalAddressRow
    } yield AddressRowModel(
      mainAddressVal,
      postalAddressVal
    )
  }

  def getPersonDetailsTable(
    ninoToDisplay: Option[Nino],
    name: Option[String]
  )(implicit request: UserRequest[_]): Future[Seq[PersonalDetailsTableRowModel]] = {
    val nameRow: Option[PersonalDetailsTableRowModel] =
      name.map(name =>
        PersonalDetailsTableRowModel(
          "name",
          "label.name",
          HtmlFormat.raw(TemplateFunctions.upperCaseToTitleCase(name)),
          "label.change",
          "label.your_name",
          Some(configDecorator.changeNameLinkUrl),
          displayChangelink = request.trustedHelper.isEmpty
        )
      )

    val ninoRow: Option[PersonalDetailsTableRowModel] =
      ninoToDisplay.map(n =>
        PersonalDetailsTableRowModel(
          "national_insurance",
          "label.national_insurance",
          formattedNino(n),
          "label.view_national_insurance_letter",
          "",
          Some(configDecorator.ptaNinoSaveUrl)
        )
      )
    Future.successful(Seq(nameRow, ninoRow).flatten[PersonalDetailsTableRowModel])
  }

  def getTrustedHelpersRow(implicit
    messages: play.api.i18n.Messages
  ): Option[PersonalDetailsTableRowModel] =
    Some(
      PersonalDetailsTableRowModel(
        "trusted_helpers",
        "label.trusted_helpers",
        HtmlFormat.raw(messages("label.manage_trusted_helpers")),
        "label.manage",
        "label.your_trusted_helpers",
        Some(configDecorator.manageTrustedHelpersUrl)
      )
    )

  def getManageTaxAgentsRow(implicit
    messages: play.api.i18n.Messages,
    request: UserRequest[_]
  ): Option[PersonalDetailsTableRowModel] =
    Some(
      PersonalDetailsTableRowModel(
        "manage_tax_agents",
        "label.manage_tax_agents",
        HtmlFormat.raw(messages("label.add_view_change_tax_agents")),
        "label.manage",
        "label.your_tax_agents",
        Some(configDecorator.manageTaxAgentsUrl(request.uri))
      )
    )

  def getPaperlessSettingsRow(implicit
    request: UserRequest[_],
    messages: play.api.i18n.Messages,
    ec: ExecutionContext
  ): Future[Option[PersonalDetailsTableRowModel]] =
    preferencesFrontendConnector
      .getPaperlessStatus(request.uri, messages("label.continue"))
      .fold(
        _ => None,
        response =>
          Some(
            PersonalDetailsTableRowModel(
              "paperless",
              messages("label.paperless_settings"),
              HtmlFormat.raw(messages(response.responseText)),
              messages(response.linkText),
              messages(response.hiddenText.getOrElse("")),
              Some(response.link),
              displayChangelink = request.trustedHelper.isEmpty
            )
          )
      )

  def getSignInDetailsRow(implicit
    request: UserRequest[_],
    messages: play.api.i18n.Messages
  ): Option[PersonalDetailsTableRowModel] =
    request.profile.map { profileUrl =>
      PersonalDetailsTableRowModel(
        "sign_in_details",
        "label.sign_in_details",
        HtmlFormat.raw(messages("label.sign_in_details_content")),
        "label.change",
        "label.your_gg_details",
        Some(profileUrl),
        displayChangelink = request.trustedHelper.isEmpty
      )
    }
}
