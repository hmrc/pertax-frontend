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

package models.admin

import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlagName

object AllFeatureFlags {
  val list: List[FeatureFlagName] = List(
    AddressTaxCreditsBrokerCallToggle,
    TaxcalcToggle,
    NationalInsuranceTileToggle,
    ItsAdvertisementMessageToggle,
    TaxComponentsToggle,
    RlsInterruptToggle,
    PaperlessInterruptToggle,
    TaxSummariesTileToggle,
    SingleAccountCheckToggle,
    TaxcalcMakePaymentLinkToggle,
    ShowNpsOutageBannerToggle,
    AppleSaveAndViewNIToggle,
    SCAWrapperToggle,
    HmrcAccountToggle,
    AgentClientAuthorisationToggle,
    PertaxBackendToggle,
    BreathingSpaceIndicatorToggle,
    AddressJourneyEnforcerToggle,
    GetPersonFromCitizenDetailsToggle,
    DfsDigitalFormFrontendShuttered
  )
}

case object AddressTaxCreditsBrokerCallToggle extends FeatureFlagName {
  override val name: String                = "address-tax-credits-broker-call"
  override val description: Option[String] = Some(
    "If enabled do not ask tax credits question but call tax-credits-broker instead"
  )
}

case object TaxcalcToggle extends FeatureFlagName {
  override val name: String                = "taxcalc"
  override val description: Option[String] = Some("Enable/disable the tile for payments and repayments")
}

case object NationalInsuranceTileToggle extends FeatureFlagName {
  override val name: String                = "national-insurance-tile"
  override val description: Option[String] = Some("Enable/disable the tile for check your National Insurance")
}

case object ItsAdvertisementMessageToggle extends FeatureFlagName {
  override val name: String                = "itsa-advertisement-message"
  override val description: Option[String] = Some("Enable/disable the advertisement message for ITSA")
}

case object TaxComponentsToggle extends FeatureFlagName {
  override val name: String                = "tax-components"
  override val description: Option[String] = Some("Check your income tax")
}

case object RlsInterruptToggle extends FeatureFlagName {
  override val name: String                = "rls-interrupt-toggle"
  override val description: Option[String] = Some("Enable/disable the interrupt for return letter to sender (RLS)")
}

case object PaperlessInterruptToggle extends FeatureFlagName {
  override val name: String                = "enforce-paperless-preference"
  override val description: Option[String] = Some("Enable/disable the interrupt for paperless setting")
}

case object TaxSummariesTileToggle extends FeatureFlagName {
  override val name: String                = "tax-summaries-tile"
  override val description: Option[String] = Some("Enable/disable the tile for annual tax summary")
}

case object SingleAccountCheckToggle extends FeatureFlagName {
  override val name: String                = "single-account-check"
  override val description: Option[String] = Some("Enable/disable single account check")
}

case object TaxcalcMakePaymentLinkToggle extends FeatureFlagName {
  override val name: String                = "taxcalc-make-payment-link-toggle"
  override val description: Option[String] = Some("Enable/disable direct link to make a payment on taxcalc tile")
}

case object ShowNpsOutageBannerToggle extends FeatureFlagName {
  override val name: String                = "show-nps-outage-banner"
  override val description: Option[String] = Some("Enable/disable the show nps outage banner")
}

case object AppleSaveAndViewNIToggle extends FeatureFlagName {
  override val name: String                = "apple-save-view-ni-toggle"
  override val description: Option[String] = Some("Enable/disable the new content for the Apple Save and View NI")
}

case object SCAWrapperToggle extends FeatureFlagName {
  override val name: String                = "sca-wrapper-toggle"
  override val description: Option[String] = Some(
    "Enable/disable the SCA Wrapper layout"
  )
}

case object HmrcAccountToggle extends FeatureFlagName {
  override val name: String                = "hmrc-account-toggle"
  override val description: Option[String] = Some(
    "Enable/disable the hmrc-account service replacing the Profile and Settings page"
  )
}

case object AgentClientAuthorisationToggle extends FeatureFlagName {
  override val name: String                = "agent-client-authorisation-toggle"
  override val description: Option[String] = Some(
    "Enable/disable calls to agent-client-authorisation service from Profile and Setting page"
  )
}

case object PertaxBackendToggle extends FeatureFlagName {
  override val name: String                = "pertax-backend-toggle"
  override val description: Option[String] = Some(
    "Enable/disable pertax backend during auth"
  )
}

case object BreathingSpaceIndicatorToggle extends FeatureFlagName {
  override val name: String                = "breathing-space-indicator-toggle"
  override val description: Option[String] = Some(
    "Enable/disable calls to Breathing space"
  )
}

case object AddressJourneyEnforcerToggle extends FeatureFlagName {
  override val name: String                = "address-journey-enforcer-toggle"
  override val description: Option[String] = Some(
    "Enable/disable call to for address journey"
  )
}

case object GetPersonFromCitizenDetailsToggle extends FeatureFlagName {
  override val name: String                = "get-person-from-citizen-details-toggle"
  override val description: Option[String] = Some(
    "Enable/disable call to designatory-details"
  )
}

case object DfsDigitalFormFrontendShuttered extends FeatureFlagName {
  override val name: String                = "dfs-digital-forms-frontend-shuttered"
  override val description: Option[String] = Some(
    "Enable/disable indicating whether the dfs digital forms frontend is shuttered"
  )
}
